package com.lifemetrics.backend.ptsheet;

import com.google.auth.oauth2.GoogleCredentials;
import com.lifemetrics.backend.ptsheet.PtSheetParser.CardioEntry;
import com.lifemetrics.backend.ptsheet.PtSheetParser.ExerciseEntry;
import com.lifemetrics.backend.ptsheet.PtSheetParser.SessionEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 구글 시트("헬스 운동기록" > "PT운동 일정" / "유산소" 탭)의 운동일지를 exercise_session / exercise_log / exercise_set 에 동기화.
 * data_pipeline/health/scripts/sync_pt_sheet.py 를 백엔드로 옮긴 것 — 새벽 배치(PtSheetScheduler)와 화면의 [기록 불러오기]가 부른다.
 * <ul>
 *   <li>PT 세션: 같은 session_no 가 이미 들어갔으면({@code [sheet#번호]} 마커) 통째로 건너뛴다. 운동 종목은 매번 대조해 없으면 추가.</li>
 *   <li>유산소: 줄마다 {@code [cardio#날짜#종목#n]} 마커로 넣거나 고치고, 시트에서 사라진 줄은 지운다(시트가 원본).</li>
 *   <li>파이썬의 --reset(이전 기록 지우고 다시 넣기)은 옮기지 않았다.</li>
 * </ul>
 * 인증: 서비스 계정 키(drive.readonly). 시트를 키의 client_email 에 뷰어로 공유해야 읽힌다.
 */
@Slf4j
@Service
public class PtSheetSyncService {

    static final long USER_ID = 1L;
    static final String CARDIO_CATEGORY = "유산소";
    /** 유산소 탭은 한글 종목명만 적으므로, 기존 종목(영문명)과 묶기 위한 영문명 사전. */
    static final Map<String, String> CARDIO_EN = Map.ofEntries(
            Map.entry("러닝머신", "Treadmill"), Map.entry("런닝머신", "Treadmill"), Map.entry("트레드밀", "Treadmill"),
            Map.entry("트레드 밀", "Treadmill"), Map.entry("천국의 계단", "Stair Climber"), Map.entry("천국의계단", "Stair Climber"),
            Map.entry("스텝밀", "Stair Climber"), Map.entry("실내 사이클", "Indoor Cycle"), Map.entry("실내사이클", "Indoor Cycle"),
            Map.entry("사이클", "Indoor Cycle"), Map.entry("로잉머신", "Rowing Machine"), Map.entry("로잉 머신", "Rowing Machine"),
            Map.entry("일립티컬", "Elliptical"), Map.entry("줄넘기", "Jump Rope"));
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Pattern DATE_RE = Pattern.compile("^(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})");

    public record Result(String syncedAt, int sessionsImported, List<String> skippedSessionNos, int logs, int sets,
                         List<String> newCategories, List<String> newItems, int filledNames,
                         Integer cardioRows, int cardioNew, int cardioUpdated, int cardioDeleted,
                         List<String> anomalies, List<String> warnings, List<String> touchedDates) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String sheetId;
    private final Path serviceAccountFile;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Result last;

    public PtSheetSyncService(@Qualifier("ridingDataSource") DataSource dataSource,
                              @Qualifier("ridingTransactionManager") PlatformTransactionManager txManager,
                              @Value("${pt-sheet.id:1NfhQvpDkp0LvcMbqPvpmBKVH5wXGI-lmdVETkxlHnUk}") String sheetId,
                              @Value("${pt-sheet.service-account-file:${PT_SHEET_SA_FILE:/run/secrets/pt_sheet_service_account.json}}") String saFile) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
        this.sheetId = sheetId;
        this.serviceAccountFile = Path.of(saFile);
    }

    public Optional<Result> last() {
        return Optional.ofNullable(last);
    }

    /** 시트를 받아 DB 에 반영. 이미 다른 동기화가 돌고 있으면 409. */
    public Result sync() {
        if (!lock.tryLock()) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 시트 동기화가 진행 중입니다. 잠시 뒤 다시 눌러 주세요.");
        try {
            byte[] xlsx = download();
            Result r = syncXlsx(xlsx);
            last = r;
            log.info("PT 시트 동기화: 세션 {}개 추가, 운동 {}건, 유산소 +{} ~{} -{}, 날짜 {}",
                    r.sessionsImported(), r.logs(), r.cardioNew(), r.cardioUpdated(), r.cardioDeleted(), r.touchedDates());
            return r;
        } finally {
            lock.unlock();
        }
    }

    // ── 시트 받기 ────────────────────────────────────

    private byte[] download() {
        if (!Files.isReadable(serviceAccountFile)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "구글 시트 서비스 계정 키가 서버에 없습니다 (" + serviceAccountFile + ").");
        }
        try (InputStream in = Files.newInputStream(serviceAccountFile)) {
            GoogleCredentials creds = GoogleCredentials.fromStream(in)
                    .createScoped(List.of("https://www.googleapis.com/auth/drive.readonly"));
            creds.refreshIfExpired();
            String url = "https://www.googleapis.com/drive/v3/files/" + URLEncoder.encode(sheetId, StandardCharsets.UTF_8)
                    + "/export?mimeType=" + URLEncoder.encode(XLSX_MIME, StandardCharsets.UTF_8);
            HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + creds.getAccessToken().getTokenValue())
                    .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                String body = new String(res.body(), StandardCharsets.UTF_8);
                log.warn("PT 시트 다운로드 실패 {}: {}", res.statusCode(), body.length() > 300 ? body.substring(0, 300) : body);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "구글 시트를 받지 못했습니다 (HTTP " + res.statusCode() + ").");
            }
            return res.body();
        } catch (IOException e) {
            log.warn("PT 시트 다운로드 실패: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "구글 시트를 받지 못했습니다: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "시트 받기가 중단되었습니다.");
        }
    }

    // ── 반영 ────────────────────────────────────────

    Result syncXlsx(byte[] xlsx) {
        List<SessionEntry> sessions;
        List<CardioEntry> cardioRows;
        try (Workbook wb = PtSheetParser.open(xlsx)) {
            sessions = PtSheetParser.buildSessions(PtSheetParser.loadRows(wb, PtSheetParser.SHEET_TAB_NAME));
            cardioRows = PtSheetParser.loadCardioRows(wb, PtSheetParser.CARDIO_TAB_NAME);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "시트 파일을 읽지 못했습니다: " + e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
        return tx.execute(status -> new Run().apply(sessions, cardioRows));
    }

    /** 한 번의 동기화(트랜잭션 하나) 동안의 종목·카테고리 캐시와 결과 집계. */
    private final class Run {
        final Map<String, Long> categories = new HashMap<>();
        final Map<String, Long> itemsByKo = new HashMap<>(), itemsByEn = new HashMap<>(), itemsByName = new HashMap<>();
        final Map<Long, String[]> itemNames = new HashMap<>();   // id → {ko, en}
        final List<String> newCategories = new ArrayList<>(), newItems = new ArrayList<>();
        final List<String> skipped = new ArrayList<>(), anomalies = new ArrayList<>(), warnings = new ArrayList<>();
        final SortedSet<String> touched = new TreeSet<>();
        int imported, logs, sets, filled, cardioNew, cardioUpdated, cardioDeleted;

        Result apply(List<SessionEntry> sessions, List<CardioEntry> cardioRows) {
            loadItems();
            for (SessionEntry s : sessions) applySession(s);

            Integer cardioCount = null;
            if (cardioRows != null) {
                if (!cardioReady()) {
                    warnings.add("exercise_log 에 distance_km/kcal/avg_hr 컬럼이 없어 유산소 탭은 건너뜀 (exercise_cardio.sql 적용 필요)");
                } else {
                    applyCardio(cardioRows);
                    cardioCount = cardioRows.size();
                }
            }
            return new Result(LocalDateTime.now(com.lifemetrics.backend.service.DailyAnalysisService.KST).withNano(0).toString(),
                    imported, skipped, logs, sets, newCategories, newItems, filled,
                    cardioCount, cardioNew, cardioUpdated, cardioDeleted, anomalies, warnings, new ArrayList<>(touched));
        }

        void loadItems() {
            jdbc.query("SELECT id, name FROM exercise_category", rs -> {
                categories.put(rs.getString("name"), rs.getLong("id"));
            });
            jdbc.query("SELECT id, name, name_ko, name_en FROM exercise_item ORDER BY id", rs -> {
                long id = rs.getLong("id");
                String ko = rs.getString("name_ko"), en = rs.getString("name_en"), name = rs.getString("name");
                if (ko != null && !ko.isEmpty()) itemsByKo.putIfAbsent(PtSheetParser.normName(ko), id);
                if (en != null && !en.isEmpty()) itemsByEn.putIfAbsent(PtSheetParser.normName(en), id);
                if (name != null && !name.isEmpty()) itemsByName.putIfAbsent(PtSheetParser.normName(name), id);
                itemNames.put(id, new String[]{ko, en});
            });
        }

        void applySession(SessionEntry s) {
            String marker = "[sheet#" + s.sessionNo() + "]";
            List<String> tokens = PtSheetParser.normalizeBodyPartTokens(s.bodyPart());
            Integer already = jdbc.queryForObject("SELECT COUNT(*) FROM exercise_log WHERE memo LIKE :m",
                    new MapSqlParameterSource("m", escapeLike(marker) + "%"), Integer.class);
            if (already != null && already > 0) {
                skipped.add(s.sessionNo());
                // 이미 넣은 세션이어도 종목은 대조해서 없으면 추가(나중에 시트에 행을 더 적거나 이름을 고친 경우). 기록은 안 건드림.
                for (ExerciseEntry e : s.exercises()) item(e.nameKo(), e.nameEn(), PtSheetParser.inferCategory(e.nameKo(), e.nameEn(), tokens));
                return;
            }
            if (s.exercises().isEmpty()) return;
            LocalDate date = parseDate(s.date());
            if (date == null) {
                warnings.add("세션 " + s.sessionNo() + ": 날짜를 알 수 없어 건너뜀 (" + s.date() + ")");
                return;
            }
            long sessionId = session(date, true);
            touched.add(date.toString());

            for (ExerciseEntry e : s.exercises()) {
                long itemId = item(e.nameKo(), e.nameEn(), PtSheetParser.inferCategory(e.nameKo(), e.nameEn(), tokens));
                BigDecimal weight = avg(e.perSetWeight(), 2);
                BigDecimal reps = avg(e.perSetReps(), 0);

                StringBuilder memo = new StringBuilder(marker);
                if (!e.memo().isEmpty()) memo.append(' ').append(e.memo());
                if (!e.notes().isEmpty()) memo.append(" (").append(String.join("; ", e.notes())).append(')');
                if (!e.notes().isEmpty()) {
                    anomalies.add("[" + s.sessionNo() + "] " + s.date() + " " + (e.nameKo().isEmpty() ? e.nameEn() : e.nameKo())
                            + " | weight=" + e.weightRaw() + " reps=" + e.repsRaw() + " | " + String.join("; ", e.notes()));
                }
                logs++;
                sets += e.sets();

                GeneratedKeyHolder key = new GeneratedKeyHolder();
                jdbc.update("""
                        INSERT INTO exercise_log (session_id, exercise_item_id, weight, reps, sets, memo)
                        VALUES (:sid, :iid, :w, :r, :sets, :memo)
                        """, new MapSqlParameterSource("sid", sessionId).addValue("iid", itemId).addValue("w", weight)
                        .addValue("r", reps == null ? null : reps.intValue()).addValue("sets", e.sets())
                        .addValue("memo", memo.toString()), key, new String[]{"id"});
                long logId = key.getKey().longValue();
                for (int i = 0; i < e.sets(); i++) {
                    Double w = e.perSetWeight().get(i), r = e.perSetReps().get(i);
                    jdbc.update("INSERT INTO exercise_set (log_id, set_number, weight, reps) VALUES (:lid, :n, :w, :r)",
                            new MapSqlParameterSource("lid", logId).addValue("n", i + 1)
                                    .addValue("w", w == null ? null : BigDecimal.valueOf(w))
                                    .addValue("r", r == null ? null : (int) Math.round(r)));
                }
            }
            imported++;
        }

        void applyCardio(List<CardioEntry> rows) {
            Map<String, Map<String, Object>> existing = new LinkedHashMap<>();
            for (Map<String, Object> r : jdbc.queryForList("""
                    SELECT l.id, l.session_id, SUBSTRING_INDEX(l.memo, ']', 1) AS marker, l.exercise_item_id,
                           l.duration_sec, l.distance_km, l.kcal, l.avg_hr, l.memo
                    FROM exercise_log l WHERE l.memo LIKE '[cardio#%' ORDER BY l.id
                    """, new MapSqlParameterSource())) {
                existing.put(r.get("marker") + "]", r);
            }
            // 시트를 잘못 받아 유산소 탭이 통째로 비면 DB 의 유산소 기록이 전부 지워진다 → 그 경우는 지우지 않는다.
            boolean allowDelete = !rows.isEmpty();

            for (CardioEntry c : rows) {
                long itemId = item(c.name(), CARDIO_EN.getOrDefault(c.name(), ""), CARDIO_CATEGORY);
                String memo = String.join(" ", nonEmpty(c.marker(), c.speedIncline(), c.memo()));
                Integer dur = c.durationMin() == null ? null : (int) PtSheetParser.roundHalfEven(c.durationMin() * 60, 0);
                BigDecimal dist = c.distanceKm() == null ? null : BigDecimal.valueOf(PtSheetParser.roundHalfEven(c.distanceKm(), 2));
                MapSqlParameterSource vals = new MapSqlParameterSource("iid", itemId).addValue("dur", dur).addValue("dist", dist)
                        .addValue("kcal", c.kcal()).addValue("hr", c.avgHr()).addValue("memo", memo);

                Map<String, Object> old = existing.remove(c.marker());
                LocalDate date = LocalDate.parse(c.date());
                if (old == null) {
                    long sid = session(date, false);
                    jdbc.update("""
                            INSERT INTO exercise_log (session_id, exercise_item_id, sets, duration_sec, distance_km, kcal, avg_hr, memo)
                            VALUES (:sid, :iid, 1, :dur, :dist, :kcal, :hr, :memo)
                            """, vals.addValue("sid", sid));
                    cardioNew++;
                    touched.add(c.date());
                    continue;
                }
                boolean same = Objects.equals(toLong(old.get("exercise_item_id")), itemId)
                        && Objects.equals(toInt(old.get("duration_sec")), dur)
                        && sameDecimal(old.get("distance_km"), dist)
                        && Objects.equals(toInt(old.get("kcal")), c.kcal())
                        && Objects.equals(toInt(old.get("avg_hr")), c.avgHr())
                        && Objects.equals(old.get("memo"), memo);
                if (!same) {
                    jdbc.update("""
                            UPDATE exercise_log SET exercise_item_id = :iid, duration_sec = :dur, distance_km = :dist,
                                kcal = :kcal, avg_hr = :hr, memo = :memo WHERE id = :id
                            """, vals.addValue("id", old.get("id")));
                    cardioUpdated++;
                    touched.add(c.date());
                }
            }
            if (!existing.isEmpty() && !allowDelete) {
                warnings.add("유산소 탭이 비어 있어 기존 유산소 기록 " + existing.size() + "건은 지우지 않음");
                return;
            }
            // 시트에서 지워진 줄 → DB 에서도 삭제. 그 때문에 빈 비-PT 세션이 되면 세션도 삭제(파이썬과 같음).
            for (Map<String, Object> old : existing.values()) {
                jdbc.update("DELETE FROM exercise_log WHERE id = :id", new MapSqlParameterSource("id", old.get("id")));
                jdbc.update("""
                        DELETE FROM exercise_session WHERE id = :sid AND is_pt = 0
                          AND NOT EXISTS (SELECT 1 FROM exercise_log WHERE session_id = :sid)
                        """, new MapSqlParameterSource("sid", old.get("session_id")));
                cardioDeleted++;
            }
        }

        boolean cardioReady() {
            Integer n = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                      AND table_name = 'exercise_log' AND column_name IN ('distance_km', 'kcal', 'avg_hr')
                    """, new MapSqlParameterSource(), Integer.class);
            return n != null && n == 3;
        }

        long category(String name) {
            Long id = categories.get(name);
            if (id != null) return id;
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update("INSERT INTO exercise_category (name) VALUES (:n)", new MapSqlParameterSource("n", name), key, new String[]{"id"});
            id = key.getKey().longValue();
            categories.put(name, id);
            newCategories.add(name);
            return id;
        }

        long item(String nameKo, String nameEn, String categoryName) {
            String kKo = PtSheetParser.normName(nameKo), kEn = PtSheetParser.normName(nameEn);
            Object[][] lookups = {{kKo, itemsByKo}, {kEn, itemsByEn}, {kKo, itemsByName}, {kEn, itemsByName}};
            for (Object[] l : lookups) {
                String k = (String) l[0];
                @SuppressWarnings("unchecked") Map<String, Long> index = (Map<String, Long>) l[1];
                if (!k.isEmpty() && index.containsKey(k)) {
                    long id = index.get(k);
                    fillMissingNames(id, nameKo, nameEn);
                    return id;
                }
            }
            long cid = category(categoryName);
            String display = nameKo != null && !nameKo.isEmpty() ? nameKo : nameEn;
            // (category_id, name) 유니크 — 같은 이름이 이미 있으면 그걸 쓴다
            List<Long> same = jdbc.queryForList("SELECT id FROM exercise_item WHERE category_id = :c AND name = :n",
                    new MapSqlParameterSource("c", cid).addValue("n", display), Long.class);
            long id;
            if (!same.isEmpty()) {
                id = same.get(0);
            } else {
                GeneratedKeyHolder key = new GeneratedKeyHolder();
                jdbc.update("""
                        INSERT INTO exercise_item (category_id, name, name_ko, name_en, is_active) VALUES (:c, :n, :ko, :en, 1)
                        """, new MapSqlParameterSource("c", cid).addValue("n", display)
                        .addValue("ko", emptyToNull(nameKo)).addValue("en", emptyToNull(nameEn)), key, new String[]{"id"});
                id = key.getKey().longValue();
                newItems.add("[" + categoryName + "] " + nameKo + " / " + nameEn);
            }
            if (!kKo.isEmpty()) itemsByKo.put(kKo, id);
            if (!kEn.isEmpty()) itemsByEn.put(kEn, id);
            itemsByName.putIfAbsent(PtSheetParser.normName(display), id);
            itemNames.putIfAbsent(id, new String[]{emptyToNull(nameKo), emptyToNull(nameEn)});
            return id;
        }

        /** 기존 종목에 비어 있는 한/영 이름을 시트 값으로 채운다(있는 값은 안 바꿈). */
        void fillMissingNames(long id, String nameKo, String nameEn) {
            String[] cur = itemNames.get(id);
            if (cur == null) return;
            String[] cols = {"name_ko", "name_en"};
            String[] vals = {nameKo, nameEn};
            List<Map<String, Long>> idx = List.of(itemsByKo, itemsByEn);
            for (int i = 0; i < 2; i++) {
                if (vals[i] != null && !vals[i].isEmpty() && (cur[i] == null || cur[i].isEmpty())) {
                    jdbc.update("UPDATE exercise_item SET " + cols[i] + " = :v WHERE id = :id AND " + cols[i] + " IS NULL",
                            new MapSqlParameterSource("v", vals[i]).addValue("id", id));
                    cur[i] = vals[i];
                    idx.get(i).putIfAbsent(PtSheetParser.normName(vals[i]), id);
                    filled++;
                }
            }
        }

        long session(LocalDate date, boolean markPt) {
            List<Long> ids = jdbc.queryForList("SELECT id FROM exercise_session WHERE user_id = :u AND session_date = :d",
                    new MapSqlParameterSource("u", USER_ID).addValue("d", date), Long.class);
            if (!ids.isEmpty()) {
                if (markPt) jdbc.update("UPDATE exercise_session SET is_pt = 1 WHERE id = :id", new MapSqlParameterSource("id", ids.get(0)));
                return ids.get(0);
            }
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update("INSERT INTO exercise_session (user_id, session_date, is_pt) VALUES (:u, :d, :pt)",
                    new MapSqlParameterSource("u", USER_ID).addValue("d", date).addValue("pt", markPt ? 1 : 0), key, new String[]{"id"});
            return key.getKey().longValue();
        }
    }

    // ── 작은 도우미 ──────────────────────────────────

    static LocalDate parseDate(String s) {
        if (s == null) return null;
        Matcher m = DATE_RE.matcher(s.strip());
        if (!m.find()) return null;
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    /** null 을 뺀 평균(파이썬 round 와 같은 은행가 반올림). 값이 없으면 null. */
    static BigDecimal avg(List<Double> values, int scale) {
        List<Double> v = values.stream().filter(Objects::nonNull).toList();
        if (v.isEmpty()) return null;
        double a = v.stream().mapToDouble(Double::doubleValue).sum() / v.size();
        return BigDecimal.valueOf(PtSheetParser.roundHalfEven(a, scale)).setScale(scale, java.math.RoundingMode.HALF_EVEN);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static List<String> nonEmpty(String... parts) {
        List<String> out = new ArrayList<>();
        for (String p : parts) if (p != null && !p.isEmpty()) out.add(p);
        return out;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static Integer toInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static boolean sameDecimal(Object db, BigDecimal v) {
        if (db == null || v == null) return db == null && v == null;
        return new BigDecimal(db.toString()).compareTo(v) == 0;
    }
}

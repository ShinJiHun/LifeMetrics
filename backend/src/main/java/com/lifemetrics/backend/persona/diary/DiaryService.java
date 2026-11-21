package com.lifemetrics.backend.persona.diary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.client.ClaudeChatClient;
import com.lifemetrics.backend.service.DailyAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Stream;

/**
 * 휴먼 일기: 하루 메모(diary_note) + 그날 운동·라이딩·식단·걸음 수 기록 + 업무 일지 →문체 가이드(resources/diary/style-guide.md)대로 일기 초안(diary_entry).
 * 초안은 관리자가 확인·수정하고 게시해야 journal_post(휴먼 블로그)에 올라간다.
 * <p>
 * 메모가 들어오는 길: 화면에서 붙여넣기·파일 올리기, 또는 NAS 폴더({@code diary.inbox-dir}/YYYY-MM-DD/*.md) —
 * 서버에서 Claude Code 로 "메모 저장해줘" 하면 그 폴더에 쓰고, 화면을 열거나 초안을 만들 때 가져온다.
 */
@Slf4j
@Service
public class DiaryService {

    static final long USER_ID = 1L;
    private static final int MAX_NOTE_BYTES = 1024 * 1024;
    /**
     * 일기에 넣는 앱 기록. 치료는 블로그 글감이 아니라서 넣지 않는다.
     * 배치가 새벽에 전날 것을 쓰므로 자정 동기화되는 폰 일일 활동(걸음 수 등)과 식단도 넣는다.
     * (같은 날 [지금 만들기]를 누르면 걸음 수가 덜 들어 있을 수 있다 — 문체 가이드에서 "적다"고 해석하지 않게 한다.)
     */
    private static final List<String> RECORD_KEYS =
            List.of("fitness", "rides", "bodyRecords", "phoneExercises", "dailyActivity", "meals");
    /** 업무 일지(work_log_entry)를 일기 재료로 넣을 때 쓰는 키. */
    static final String WORK_LOG_KEY = "workLog";
    /** 업로드 원본을 NAS 에 둘 때 쓰는 하위 폴더. 메모를 지우면 원본은 여기로 옮겨 다시 가져오지 않게 한다. */
    private static final String DELETED_DIR = "deleted";

    public record Note(long id, String noteDate, String source, String fileName, String content, String createdAt) {}
    public record Entry(long id, String entryDate, String kind, String title, String content, List<String> warnings,
                        String status, boolean edited, Long postId, String generatedAt, String publishedAt) {}
    public record Day(String date, List<Note> notes, Entry diary, Entry book, Map<String, Object> records,
                      List<LectureService.Lecture> lectures) {}
    public record SubMenu(long id, String category, String name) {}
    /** lectureDefault: 강연 요약을 올릴 '강연' 소메뉴. 아직 없으면 null(첫 게시 때 '일상' 아래 만든다). */
    public record SubMenus(List<SubMenu> subs, long diaryDefault, Long lectureDefault) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ClaudeChatClient claude;
    private final DailyAnalysisService dailyAnalysis;
    private final ObjectMapper objectMapper;
    private final Path inboxDir;
    private final LectureService lectures;
    /** NAS inbox 에 만든 파일·폴더의 소유자(uid:gid). 컨테이너가 root 로 돌아 root 소유로 생기는 것을 호스트 계정으로 돌린다. 비우면 그대로. */
    private final int[] inboxOwner;

    public DiaryService(@Qualifier("journalDataSource") DataSource dataSource, ClaudeChatClient claude,
                        DailyAnalysisService dailyAnalysis, ObjectMapper objectMapper, LectureService lectures,
                        @Value("${diary.inbox-dir:${DIARY_INBOX_DIR:/mnt/200gb/NAS/persona/diary/inbox}}") String inboxDir,
                        @Value("${diary.inbox-owner:${DIARY_INBOX_OWNER:}}") String inboxOwner) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.claude = claude;
        this.dailyAnalysis = dailyAnalysis;
        this.objectMapper = objectMapper;
        this.lectures = lectures;
        this.inboxDir = Path.of(inboxDir);
        this.inboxOwner = parseOwner(inboxOwner);
    }

    /** "1003:1006" → {1003, 1006}. 비었거나 형식이 틀리면 null(소유자를 바꾸지 않음). */
    static int[] parseOwner(String s) {
        if (s == null || !s.strip().matches("\\d+:\\d+")) return null;
        String[] p = s.strip().split(":");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
    }

    /** 새로 만든 파일·폴더를 호스트 계정 소유로. 실패해도(권한 없음 등) 로그만 남긴다. */
    private void chown(Path p) {
        if (inboxOwner == null) return;
        try {
            Files.setAttribute(p, "unix:uid", inboxOwner[0], java.nio.file.LinkOption.NOFOLLOW_LINKS);
            Files.setAttribute(p, "unix:gid", inboxOwner[1], java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (Exception e) {
            log.debug("일기 inbox 소유자 변경 실패 {}: {}", p, e.getMessage());
        }
    }

    /** dir 을 만들고, 이번에 새로 생긴 폴더(inboxDir 포함, 그 위는 제외)는 소유자를 바꾼다. */
    private Path createInboxDirs(Path dir) throws IOException {
        List<Path> created = new ArrayList<>();
        for (Path d = dir; d != null && d.startsWith(inboxDir) && !Files.exists(d); d = d.getParent()) created.add(d);
        Files.createDirectories(dir);
        for (Path d : created) chown(d);
        return dir;
    }

    // ── 하루 화면 ────────────────────────────────────

    public Day day(LocalDate date) {
        importFolder(date);
        return new Day(date.toString(), notes(date), entry(date, "diary").orElse(null), entry(date, "book").orElse(null),
                records(date), lectures.forDate(date));
    }

    /** 최근 날짜별 메모 수·초안 상태 (달력 대신 쓰는 목록). */
    public List<Map<String, Object>> recent(int days) {
        LocalDate from = LocalDate.now(DailyAnalysisService.KST).minusDays(days);
        return jdbc.queryForList("""
                SELECT d.dt AS date,
                       (SELECT COUNT(*) FROM diary_note n WHERE n.note_date = d.dt) AS notes,
                       (SELECT e.status FROM diary_entry e WHERE e.entry_date = d.dt AND e.kind = 'diary') AS diary
                FROM (SELECT note_date AS dt FROM diary_note WHERE note_date >= :from
                      UNION SELECT entry_date FROM diary_entry WHERE entry_date >= :from) d
                ORDER BY d.dt DESC
                """, new MapSqlParameterSource("from", from));
    }

    // ── 메모 ────────────────────────────────────────

    public List<Note> notes(LocalDate date) {
        return jdbc.query("""
                SELECT id, note_date, source, file_name, content, created_at FROM diary_note
                WHERE note_date = :d ORDER BY id
                """, new MapSqlParameterSource("d", date), (rs, i) -> new Note(rs.getLong("id"),
                rs.getDate("note_date").toLocalDate().toString(), rs.getString("source"), rs.getString("file_name"),
                rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime().toString()));
    }

    public Note addNote(LocalDate date, String source, String fileName, String content) {
        if (content == null || content.isBlank()) throw bad("메모 내용이 비어 있습니다.");
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_NOTE_BYTES) throw bad("메모가 너무 큽니다 (최대 1MB).");
        String src = switch (source == null ? "" : source) {
            case "app", "cli", "manual" -> source;
            default -> "manual";
        };
        String name = inboxFileName(fileName, src);
        if (name != null && jdbc.queryForObject("""
                SELECT COUNT(*) FROM diary_note WHERE note_date = :d AND source = :s AND file_name = :f
                """, new MapSqlParameterSource("d", date).addValue("s", src).addValue("f", name), Integer.class) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 올린 파일입니다: " + name);
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO diary_note (note_date, source, file_name, content, created_at)
                VALUES (:d, :s, :f, :c, :t)
                """, new MapSqlParameterSource("d", date).addValue("s", src).addValue("f", name)
                .addValue("c", content.strip()).addValue("t", now()), key, new String[]{"id"});
        long id = key.getKey().longValue();
        if (name != null) saveToInbox(date, name, content.strip());
        return notes(date).stream().filter(n -> n.id() == id).findFirst().orElseThrow();
    }

    public void deleteNote(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT note_date, file_name FROM diary_note WHERE id = :id",
                new MapSqlParameterSource("id", id));
        jdbc.update("DELETE FROM diary_note WHERE id = :id", new MapSqlParameterSource("id", id));
        lectures.onNoteDeleted(id);
        if (rows.isEmpty() || rows.get(0).get("file_name") == null) return;
        // 폴더에 원본이 남아 있으면 다음 화면 열기 때 다시 가져온다 → deleted/ 로 옮겨 보관만 한다(지우지 않음).
        LocalDate date = ((java.sql.Date) rows.get(0).get("note_date")).toLocalDate();
        String name = (String) rows.get(0).get("file_name");
        Path dir = inboxDir.resolve(date.toString());
        Path src = dir.resolve(name).normalize();
        if (!src.startsWith(dir) || !Files.isRegularFile(src)) return;
        try {
            Path trash = createInboxDirs(dir.resolve(DELETED_DIR));
            Files.move(src, trash.resolve(System.currentTimeMillis() + "-" + name));
        } catch (IOException e) {
            log.warn("일기 메모 원본 옮기기 실패 {}: {}", src, e.getMessage());
        }
    }

    /** 폴더에서 가져올 때 파일 이름으로 정하는 출처. app* → Claude 앱, manual* → 직접 입력, 나머지 → Claude Code. */
    static String folderSource(String name) {
        return name.startsWith("app") ? "app" : name.startsWith("manual") ? "manual" : "cli";
    }

    /**
     * 화면에서 올린 파일을 NAS 에 둘 이름. 경로 문자를 없애고 .md/.txt 로 끝나게 하며,
     * 폴더 재스캔 때 같은 출처로 잡히도록(중복 방지) 이름이 가리키는 출처가 다르면 "출처-" 를 앞에 붙인다.
     */
    static String inboxFileName(String fileName, String source) {
        if (fileName == null || fileName.isBlank()) return null;
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}:*?\"<>|]", "_").strip();
        if (base.startsWith(".")) base = "_" + base.substring(1);
        if (base.isBlank()) base = "note";
        if (!base.endsWith(".md") && !base.endsWith(".txt")) base = base + ".md";
        if (!folderSource(base).equals(source)) base = source + "-" + base;
        if (base.length() > 200) base = base.substring(0, 196) + base.substring(base.lastIndexOf('.'));
        return base;
    }

    /** 업로드 원본을 NAS inbox/날짜/ 에 보관. 실패해도 메모(DB)는 이미 저장됐으므로 로그만 남긴다. */
    private void saveToInbox(LocalDate date, String name, String content) {
        try {
            Path dir = createInboxDirs(inboxDir.resolve(date.toString()));
            Path file = dir.resolve(name).normalize();
            if (!file.startsWith(dir) || Files.exists(file)) return;
            Files.writeString(file, content, StandardCharsets.UTF_8);
            chown(file);
        } catch (IOException e) {
            log.warn("일기 메모 원본 NAS 보관 실패 {}/{}: {}", date, name, e.getMessage());
        }
    }

    /**
     * NAS 폴더의 그날 파일을 메모로 가져온다. 이미 가져온 파일(날짜·출처·파일명 같음)은 건너뛴다.
     * 파일명이 app 으로 시작하면 Claude 앱, manual 이면 직접 입력, 나머지는 Claude Code(cli) 메모로 본다.
     */
    public int importFolder(LocalDate date) {
        Path dir = inboxDir.resolve(date.toString());
        if (!Files.isDirectory(dir)) return 0;
        int added = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.sorted().toList()) {
                String name = p.getFileName().toString();
                if (!Files.isRegularFile(p) || !(name.endsWith(".md") || name.endsWith(".txt"))) continue;
                String source = folderSource(name);
                if (Files.size(p) > MAX_NOTE_BYTES) continue;
                String content = Files.readString(p, StandardCharsets.UTF_8);
                if (content.isBlank()) continue;
                added += jdbc.update("""
                        INSERT IGNORE INTO diary_note (note_date, source, file_name, content, created_at)
                        VALUES (:d, :s, :f, :c, :t)
                        """, new MapSqlParameterSource("d", date).addValue("s", source).addValue("f", name)
                        .addValue("c", content.strip()).addValue("t", now()));
            }
        } catch (IOException e) {
            log.warn("일기 메모 폴더 읽기 실패 {}: {}", dir, e.getMessage());
        }
        if (added > 0) log.info("일기 메모 {}건 가져옴 ({})", added, date);
        return added;
    }

    // ── 초안 ────────────────────────────────────────

    /**
     * 초안을 만든다. force=false(배치)면 입력이 그대로이거나 사람이 고친 초안은 건드리지 않는다.
     * 게시된 일기는 다시 만들지 않는다.
     */
    public Optional<Day> generate(LocalDate date, boolean force) {
        importFolder(date);
        List<Note> notes = notes(date);
        Map<String, Object> records = records(date);
        // 기록만으로 쓰면 하루를 추측해서 지어내게 된다 → 메모가 하나라도 있어야 쓴다.
        if (notes.isEmpty()) {
            if (force) throw bad("메모가 없습니다. 메모를 먼저 추가해 주세요.");
            return Optional.empty();
        }
        Optional<Entry> current = entry(date, "diary");
        if (current.isPresent() && "published".equals(current.get().status())) {
            if (force) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 게시한 일기입니다.");
            return Optional.empty();
        }
        Set<Long> lectureNotes = lectures.lectureNoteIds(date);
        String hash = hash(notes, records, lectureNotes);
        if (!force && current.isPresent() && (current.get().edited() || hash.equals(currentHash(date)))) {
            return Optional.empty();
        }

        // 한국어 일기 + 독서 글이 JSON 문자열로 들어가면 4000 토큰을 넘을 수 있다(첫 시험에서 잘림). 클라이언트 읽기 제한(90초) 안에 끝나도록 8000.
        String raw = claude.chat(systemPrompt(), List.of(new ClaudeChatClient.Message("user", userPrompt(date, notes, records, lectureNotes))), 8000)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다."));
        JsonNode out = parseJson(objectMapper, raw);
        List<String> warnings = new ArrayList<>();
        for (JsonNode w : out.path("warnings")) warnings.add(w.asText());

        JsonNode diary = out.path("diary");
        if (diary.path("content").asText("").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 가 일기를 쓰지 못했습니다.");
        upsert(date, "diary", diary.path("title").asText(date + " 일기"), diary.path("content").asText(), warnings, hash);

        JsonNode book = out.path("book");
        Optional<Entry> currentBook = entry(date, "book");
        if (book.isObject() && !book.path("content").asText("").isBlank()
                && currentBook.map(b -> !"published".equals(b.status())).orElse(true)) {
            upsert(date, "book", book.path("title").asText("독서 기록"), book.path("content").asText(), List.of(), hash);
        }
        return Optional.of(day(date));
    }

    /** 그날 메모에서 강연 필기를 찾아 강연 요약 초안을 만든다(일기보다 먼저 — 일기는 강연을 한두 줄만 쓰도록). */
    public LectureService.ScanResult generateLectures(LocalDate date) {
        importFolder(date);
        return lectures.scanDate(date);
    }

    public Entry update(long id, String title, String content) {
        Entry e = entryById(id);
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "게시한 글은 블로그에서 고쳐 주세요.");
        if (title == null || title.isBlank() || content == null || content.isBlank()) throw bad("제목과 내용을 입력해 주세요.");
        jdbc.update("UPDATE diary_entry SET title = :t, content = :c, edited = 1 WHERE id = :id",
                new MapSqlParameterSource("t", title.strip()).addValue("c", content.strip()).addValue("id", id));
        return entryById(id);
    }

    public void discard(long id) {
        Entry e = entryById(id);
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "게시한 글은 지울 수 없습니다.");
        jdbc.update("DELETE FROM diary_entry WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    // ── 게시 ────────────────────────────────────────

    /** 게시할 수 있는 휴먼 소메뉴와 일기의 기본 소메뉴. */
    public SubMenus subMenus() {
        long diaryDefault = diarySubId();
        List<SubMenu> subs = humanSubs();
        return new SubMenus(subs, diaryDefault, lectures.existingLectureSubId(subs));
    }

    public LectureService.Lecture publishLecture(long id, Long subId, String visibility) {
        return lectures.publish(id, subId, visibility, humanSubs());
    }

    private List<SubMenu> humanSubs() {
        return jdbc.query("""
                SELECT s.id, c.name AS category, s.name FROM journal_sub_menu s
                JOIN journal_category c ON c.id = s.category_id
                WHERE c.persona = 'human' ORDER BY c.sort_order, s.sort_order, s.id
                """, new MapSqlParameterSource(), (rs, i) -> new SubMenu(rs.getLong("id"), rs.getString("category"), rs.getString("name")));
    }

    public Entry publish(long id, Long subId, String visibility) {
        Entry e = entryById(id);
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 게시했습니다.");
        long sub = subId != null ? subId : "diary".equals(e.kind()) ? diarySubId() : -1;
        if (sub < 0) throw bad("독서 글은 올릴 소메뉴를 골라 주세요.");
        if (humanSubs().stream().noneMatch(s -> s.id() == sub)) throw bad("휴먼 페르소나의 소메뉴가 아닙니다.");
        String vis = "private".equals(visibility) ? "private" : "public";

        LocalDateTime t = now();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO journal_post (sub_id, title, content, visibility, created_at, updated_at)
                VALUES (:s, :title, :c, :v, :t, :t)
                """, new MapSqlParameterSource("s", sub).addValue("title", e.title())
                .addValue("c", DiaryMarkdown.toHtml(e.content())).addValue("v", vis).addValue("t", t), key, new String[]{"id"});
        jdbc.update("UPDATE diary_entry SET status = 'published', post_id = :p, published_at = :t WHERE id = :id",
                new MapSqlParameterSource("p", key.getKey().longValue()).addValue("t", t).addValue("id", id));
        return entryById(id);
    }

    /**
     * 일기를 올릴 기본 소메뉴. 휴먼에 '일기' 카테고리가 있으면 그 아래 올해 소메뉴(예: "26년", 없으면 첫 소메뉴),
     * 없으면 '일상 > 일기'(없으면 만든다).
     */
    private long diarySubId() {
        String yy = String.format("%02d년", LocalDate.now(DailyAnalysisService.KST).getYear() % 100);
        List<SubMenu> diaryCat = humanSubs().stream().filter(s -> "일기".equals(s.category())).toList();
        if (!diaryCat.isEmpty()) {
            return diaryCat.stream().filter(s -> s.name().contains(yy)).findFirst().orElse(diaryCat.get(0)).id();
        }
        Optional<SubMenu> daily = humanSubs().stream().filter(s -> "일상".equals(s.category()) && "일기".equals(s.name())).findFirst();
        if (daily.isPresent()) return daily.get().id();
        List<Long> cat = jdbc.queryForList("SELECT id FROM journal_category WHERE persona = 'human' AND name = '일상' ORDER BY id LIMIT 1",
                new MapSqlParameterSource(), Long.class);
        if (cat.isEmpty()) throw bad("휴먼 페르소나에 '일기' 또는 '일상' 카테고리가 없습니다.");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO journal_sub_menu (category_id, name, sort_order)
                SELECT :c, '일기', COALESCE(MAX(sort_order), 0) + 1 FROM journal_sub_menu WHERE category_id = :c
                """, new MapSqlParameterSource("c", cat.get(0)), key, new String[]{"id"});
        log.info("휴먼 '일상 > 일기' 소메뉴 생성");
        return key.getKey().longValue();
    }

    // ── 내부 ────────────────────────────────────────

    private Map<String, Object> records(LocalDate date) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, Object> all = dailyAnalysis.collectInput(USER_ID, date);
            for (String k : RECORD_KEYS) {
                Object v = all.get(k);
                if (v == null || (v instanceof Collection<?> c && c.isEmpty()) || (v instanceof Map<?, ?> m && m.isEmpty())) continue;
                out.put(k, v);
            }
        } catch (Exception e) {
            log.warn("일기용 기록 조회 실패 {}: {}", date, e.getMessage());
        }
        Map<String, Object> work = workLog(date);
        if (!work.isEmpty()) out.put(WORK_LOG_KEY, work);
        return out;
    }

    /** 그날 업무 일지(금일 실적·이슈). 명일 계획은 일기 마지막 줄 "내일 할 일" 재료. 없으면 빈 맵. */
    private Map<String, Object> workLog(LocalDate date) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT today_done, tomorrow_plan, issues FROM work_log_entry WHERE log_date = :d
                    """, new MapSqlParameterSource("d", date));
            if (rows.isEmpty()) return Map.of();
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : rows.get(0).entrySet()) {
                if (e.getValue() instanceof String s && !s.isBlank()) out.put(e.getKey(), s.strip());
            }
            return out;
        } catch (Exception e) {
            log.warn("일기용 업무 일지 조회 실패 {}: {}", date, e.getMessage());
            return Map.of();
        }
    }

    private String systemPrompt() {
        String guide;
        try {
            guide = new ClassPathResource("diary/style-guide.md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("diary/style-guide.md 를 읽지 못했습니다", e);
        }
        return guide + """


                # 출력 형식
                JSON 하나만 출력한다. 코드 블록 표시나 설명은 붙이지 않는다.
                {"diary": {"title": "...", "content": "마크다운 본문(제목 줄 없이)"},
                 "book": null 또는 {"title": "책 제목 — 한 줄 부제", "content": "마크다운 본문"},
                 "warnings": ["게시 전에 확인할 점. 실명·회사 내부 정보·부고·건강·돈 이야기가 들어갔으면 무엇인지 한 줄씩. 없으면 빈 배열"]}
                - book 은 그날 메모에 읽은 내용(읽은 범위, 인상 깊은 문장, 생각)이 있을 때만 쓴다. 책 이름이 언급됐다는 것만으로는 쓰지 않고 null.
                - content 안의 줄바꿈은 \\n 으로 이스케이프한다.
                - 메모끼리 내용이 겹치면 한 번만 쓴다. 메모 사이에 시간 순서가 보이면 그 순서를 따른다.
                - 머리에 [강연 필기]가 붙은 메모는 강연 요약을 따로 만든다. 일기에는 어떤 강연을 들었고 무엇이 남았는지 한두 줄만 쓰고,
                  강연 내용(주장·목차·인용)을 옮기지 않는다. 그 메모로 book 을 쓰지 않는다.
                """;
    }

    private String userPrompt(LocalDate date, List<Note> notes, Map<String, Object> records, Set<Long> lectureNotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("날짜: ").append(date).append(" (")
                .append(date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN)).append(")\n\n");
        sb.append("# 메모 ").append(notes.size()).append("건\n");
        for (Note n : notes) {
            sb.append("\n## 메모 ").append(n.id()).append(" · ").append(sourceLabel(n.source()));
            if (n.fileName() != null) sb.append(" · ").append(n.fileName());
            if (lectureNotes.contains(n.id())) sb.append(" · [강연 필기]");
            sb.append('\n').append(n.content()).append('\n');
        }
        Map<String, Object> app = new LinkedHashMap<>(records);
        Object work = app.remove(WORK_LOG_KEY);
        sb.append("\n# 그날 기록 (앱 데이터: fitness=헬스, rides=라이딩, phoneExercises=폰 운동, bodyRecords=체성분, ")
                .append("dailyActivity=걸음 수 등, meals=식단)\n");
        sb.append(app.isEmpty() ? "(기록 없음)" : toJson(app)).append('\n');
        sb.append("\n# 그날 업무 일지 (회사에 낸 일일 보고: today_done=금일 실적, issues=이슈, tomorrow_plan=명일 계획)\n");
        sb.append(work == null ? "(없음)" : toJson(work)).append('\n');
        return sb.toString();
    }

    private static String sourceLabel(String s) {
        return switch (s) {
            case "app" -> "Claude 앱 대화 정리";
            case "cli" -> "Claude Code 대화 정리";
            default -> "직접 쓴 메모";
        };
    }

    private void upsert(LocalDate date, String kind, String title, String content, List<String> warnings, String hash) {
        jdbc.update("""
                INSERT INTO diary_entry (entry_date, kind, title, content, warnings, status, input_hash, edited, generated_at)
                VALUES (:d, :k, :title, :c, :w, 'draft', :h, 0, :t)
                ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), warnings = VALUES(warnings),
                    input_hash = VALUES(input_hash), edited = 0, generated_at = VALUES(generated_at)
                """, new MapSqlParameterSource("d", date).addValue("k", kind)
                .addValue("title", title.length() > 255 ? title.substring(0, 255) : title).addValue("c", content.strip())
                .addValue("w", toJson(warnings)).addValue("h", hash).addValue("t", now()));
    }

    private Optional<Entry> entry(LocalDate date, String kind) {
        return jdbc.query("SELECT * FROM diary_entry WHERE entry_date = :d AND kind = :k",
                new MapSqlParameterSource("d", date).addValue("k", kind), this::mapEntry).stream().findFirst();
    }

    private Entry entryById(long id) {
        return jdbc.query("SELECT * FROM diary_entry WHERE id = :id", new MapSqlParameterSource("id", id), this::mapEntry)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "초안을 찾을 수 없습니다."));
    }

    private String currentHash(LocalDate date) {
        List<String> l = jdbc.queryForList("SELECT input_hash FROM diary_entry WHERE entry_date = :d AND kind = 'diary'",
                new MapSqlParameterSource("d", date), String.class);
        return l.isEmpty() ? null : l.get(0);
    }

    private Entry mapEntry(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        List<String> warnings = List.of();
        String w = rs.getString("warnings");
        if (w != null && !w.isBlank()) {
            try {
                warnings = objectMapper.readValue(w, new TypeReference<>() {});
            } catch (Exception ignored) {
                warnings = List.of(w);
            }
        }
        java.sql.Timestamp pub = rs.getTimestamp("published_at");
        long postId = rs.getLong("post_id");
        return new Entry(rs.getLong("id"), rs.getDate("entry_date").toLocalDate().toString(), rs.getString("kind"),
                rs.getString("title"), rs.getString("content"), warnings, rs.getString("status"), rs.getBoolean("edited"),
                rs.wasNull() ? null : postId, rs.getTimestamp("generated_at").toLocalDateTime().toString(),
                pub == null ? null : pub.toLocalDateTime().toString());
    }

    private String hash(List<Note> notes, Map<String, Object> records, Set<Long> lectureNotes) {
        StringBuilder sb = new StringBuilder();
        for (Note n : notes) {
            sb.append(n.id()).append('\u0000').append(n.content()).append(lectureNotes.contains(n.id()) ? "L" : "").append('\u0001');
        }
        sb.append(toJson(records));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 모델이 문자열 안에 줄바꿈을 그대로 넣는 경우가 있어 제어 문자를 허용해 읽는다. */
    static JsonNode parseJson(ObjectMapper objectMapper, String raw) {
        String s = raw.trim();
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        try {
            if (a < 0 || b <= a) throw new IllegalArgumentException("JSON 객체 없음");
            return objectMapper.reader()
                    .with(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                    .readTree(s.substring(a, b + 1));
        } catch (Exception e) {
            log.warn("일기 AI 응답 파싱 실패 ({}자): {} … {}", s.length(), s.substring(0, Math.min(300, s.length())),
                    s.substring(Math.max(0, s.length() - 300)));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 응답 형식이 올바르지 않습니다. 다시 만들기를 한 번 더 눌러 주세요.");
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    /** DB 기본값은 UTC 라서 앱 시각(KST)을 직접 넣는다. */
    private static LocalDateTime now() {
        return LocalDateTime.now(DailyAnalysisService.KST).withNano(0);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}

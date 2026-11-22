package com.lifemetrics.backend.persona.diary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.client.ClaudeChatClient;
import com.lifemetrics.backend.service.DailyAnalysisService;
import com.lifemetrics.backend.service.SttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 휴먼 일기: 강연 요약. 일기 메모(diary_note)에 강연 필기가 있으면 강연 요약 초안(lecture_entry)을 만든다.
 * 메모 하나에 여러 연사의 강연이 들어 있을 수 있어(카톡 '나에게 보내기' 필기 등) AI 가 먼저 강연을 나누고(1단계),
 * 강연마다 따로 요약한다(2단계, 병렬). 같은 메모에서 나온 요약은 같은 source_note_id, seq 로 구분. 원본 메모·NAS 파일은 고치지 않는다.
 * <p>
 * 메모마다 내용 해시로 판별 결과(lecture_note_scan)를 남겨, 내용이 그대로면 AI 를 다시 부르지 않는다.
 * 메모를 다시 처리할 때는 그 메모의 요약들을 연사·제목으로 짝지어 한꺼번에 갱신한다 — 사람이 고친 초안과 게시한 요약은
 * 그대로 두고(일기와 같은 규칙), 짝이 없는 손대지 않은 초안은 치우고, 새로 찾은 강연은 추가한다.
 * [버리기]한 요약은 메모가 바뀌기 전엔 되살리지 않는다.
 * 태그는 lecture_tag 로 정규화해 두고, AI 에 기존 태그 목록을 넘겨 같은 뜻이면 재사용하게 한다.
 */
@Slf4j
@Service
public class LectureService {

    /** 이보다 짧은 메모는 요약할 필기가 없다고 보고 AI 를 부르지 않는다(직접 [강연 요약 만들기]는 가능). */
    static final int MIN_LECTURE_CHARS = 200;
    static final int MAX_TAG_LENGTH = 64;
    private static final int MAX_TAGS = 20;
    private static final int AI_MAX_TAGS = 7;
    /** 메모 하나에서 나눌 강연 수 상한(AI 호출 수 제한). */
    static final int MAX_LECTURES_PER_NOTE = 10;

    /**
     * origin: note(일기 메모에서 찾음) | manual(직접 기록). lectureTime·venue·host·series·books 는 강연 정보 칸(빈 칸은 null/빈 목록),
     * memo 는 직접 기록의 내 메모. files 는 직접 기록에 올린 자료(글 내용은 빼고).
     */
    public record Lecture(long id, Long sourceNoteId, String noteFileName, String origin, String lectureDate, String lectureTime,
                          String title, String speaker, String venue, String host, String series, List<String> books, String memo,
                          String content, List<String> tags, List<String> warnings, String status, boolean edited,
                          Long postId, String generatedAt, String publishedAt, List<LectureFile> files) {
        Lecture with(List<String> tags, List<LectureFile> files) {
            return new Lecture(id, sourceNoteId, noteFileName, origin, lectureDate, lectureTime, title, speaker, venue, host,
                    series, books, memo, content, tags, warnings, status, edited, postId, generatedAt, publishedAt, files);
        }
    }
    /** 강연 정보 입력(직접 기록 만들기·카드에서 정보 고치기). date 가 비면 오늘. */
    public record Info(String title, String speaker, String date, String time, String venue, String host, String series,
                       List<String> books, String memo) {}
    /** textStatus: ready | none | pending | transcribing | failed. textChars: 요약 근거로 쓸 글 길이. */
    public record LectureFile(long id, String kind, String fileName, long sizeBytes, String textStatus, String textNote,
                              int textChars, String createdAt) {}
    public record ScanResult(int scanned, int created, List<String> errors) {}
    public record TagCount(String name, int count) {}
    public record SpeakerCount(String name, int count) {}
    public record Facets(List<TagCount> tags, List<SpeakerCount> speakers) {}

    private record NoteRow(long id, LocalDate date, String fileName, String content) {}
    /** 1단계에서 찾은 강연: 연사, 제목, 메모에서 그 부분을 찾을 단서(시각·첫 줄 등). 모르면 null. */
    record Found(String speaker, String title, String cue) {}
    /** 2단계 요약 결과. */
    record Draft(String title, String speaker, String content, List<String> tags, List<String> warnings) {}
    /** 기존 요약과 새 요약을 짝지을 때 쓰는 키. */
    record Key(String speaker, String title) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ClaudeChatClient claude;
    private final ObjectMapper objectMapper;
    private final SttService stt;

    public LectureService(@Qualifier("journalDataSource") DataSource dataSource, ClaudeChatClient claude, ObjectMapper objectMapper,
                          SttService stt) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.claude = claude;
        this.objectMapper = objectMapper;
        this.stt = stt;
    }

    // ── 조회 ────────────────────────────────────────

    public List<Lecture> forDate(LocalDate date) {
        return query("WHERE e.lecture_date = :d ORDER BY e.source_note_id IS NULL, e.source_note_id, e.seq, e.id",
                new MapSqlParameterSource("d", date));
    }

    /** 그날 메모 중 강연 필기로 판별된 메모 id (일기 초안에서 강연은 한두 줄만 쓰게 표시하는 데 쓴다). */
    public Set<Long> lectureNoteIds(LocalDate date) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT s.note_id FROM lecture_note_scan s JOIN diary_note n ON n.id = s.note_id
                WHERE n.note_date = :d AND s.is_lecture = 1
                UNION SELECT e.source_note_id FROM lecture_entry e JOIN diary_note n ON n.id = e.source_note_id
                WHERE n.note_date = :d
                """, new MapSqlParameterSource("d", date), Long.class));
    }

    /** 강연 목록. tag·speaker·기간은 비어 있으면 거르지 않는다. 연사는 부분 일치. */
    public List<Lecture> list(String tag, String speaker, LocalDate from, LocalDate to) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (from != null) {
            where.append(" AND e.lecture_date >= :from");
            p.addValue("from", from);
        }
        if (to != null) {
            where.append(" AND e.lecture_date <= :to");
            p.addValue("to", to);
        }
        if (speaker != null && !speaker.isBlank()) {
            where.append(" AND e.speaker LIKE :sp");
            p.addValue("sp", "%" + speaker.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (tag != null && !tag.isBlank()) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM lecture_entry_tag et JOIN lecture_tag t ON t.id = et.tag_id
                                 WHERE et.entry_id = e.id AND t.name = :tag)""");
            p.addValue("tag", normalizeTag(tag));
        }
        where.append(" ORDER BY e.lecture_date DESC, e.id DESC LIMIT 500");
        return query(where.toString(), p);
    }

    public Facets facets() {
        List<TagCount> tags = jdbc.query("""
                SELECT t.name, COUNT(*) AS n FROM lecture_tag t JOIN lecture_entry_tag et ON et.tag_id = t.id
                GROUP BY t.id, t.name ORDER BY n DESC, t.name
                """, new MapSqlParameterSource(), (rs, i) -> new TagCount(rs.getString("name"), rs.getInt("n")));
        List<SpeakerCount> speakers = jdbc.query("""
                SELECT speaker, COUNT(*) AS n FROM lecture_entry WHERE speaker IS NOT NULL AND speaker <> ''
                GROUP BY speaker ORDER BY n DESC, speaker
                """, new MapSqlParameterSource(), (rs, i) -> new SpeakerCount(rs.getString("speaker"), rs.getInt("n")));
        return new Facets(tags, speakers);
    }

    public Lecture byId(long id) {
        return query("WHERE e.id = :id", new MapSqlParameterSource("id", id)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강연 요약을 찾을 수 없습니다."));
    }

    // ── 만들기 ──────────────────────────────────────

    /**
     * 그날 메모를 훑어 강연 필기면 요약을 만든다. 내용이 그대로인 메모(판별 결과 있음), 고친 초안, 게시한 요약은 건드리지 않는다.
     * 메모 하나가 실패해도 나머지는 계속하고 실패 내용을 돌려준다.
     */
    public ScanResult scanDate(LocalDate date) {
        List<NoteRow> notes = jdbc.query("SELECT id, note_date, file_name, content FROM diary_note WHERE note_date = :d ORDER BY id",
                new MapSqlParameterSource("d", date), (rs, i) -> new NoteRow(rs.getLong("id"),
                        rs.getDate("note_date").toLocalDate(), rs.getString("file_name"), rs.getString("content")));
        int scanned = 0, created = 0;
        List<String> errors = new ArrayList<>();
        for (NoteRow n : notes) {
            String hash = hash(n.content());
            if (hash.equals(scanHash(n.id()))) continue;
            try {
                if (n.content().strip().length() < MIN_LECTURE_CHARS) {
                    sync(n, hash, List.of());
                    continue;
                }
                scanned++;
                created += sync(n, hash, analyze(n, false));
            } catch (Exception e) {
                log.warn("강연 요약 실패 (메모 {}): {}", n.id(), e.getMessage());
                errors.add(label(n) + ": " + reason(e));
            }
        }
        if (scanned > 0) log.info("강연 판별 {} — 메모 {}건 확인, 요약 {}건", date, scanned, created);
        return new ScanResult(scanned, created, errors);
    }

    /**
     * 메모에 강연 필기가 있다고 보고 강연을 (다시) 나눠 요약한다(메모의 [🎤 강연으로 요약]·[강연 다시 찾기]).
     * 고친 초안·게시한 요약은 그대로 두고 나머지를 한꺼번에 갱신한다. 그 메모의 요약 목록을 돌려준다.
     */
    public List<Lecture> summarizeNote(long noteId) {
        NoteRow n = note(noteId);
        sync(n, hash(n.content()), analyze(n, true));
        List<Lecture> out = byNote(noteId);
        if (out.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 가 강연 요약을 쓰지 못했습니다.");
        return out;
    }

    /** 요약 하나만 원본 메모로 다시 만든다(카드의 [다시 만들기]). 고친 초안도 덮어쓴다(화면에서 확인을 받는다). */
    public Lecture regenerate(long id) {
        Lecture e = byId(id);
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 게시한 강연 요약입니다.");
        if (e.sourceNoteId() == null) throw bad("원본 메모가 지워져 다시 만들 수 없습니다.");
        NoteRow n = note(e.sourceNoteId());
        int total = byNote(n.id()).size();
        Draft d = summarizeOne(n, new Found(e.speaker(), e.title(), null), total, allTagNames());
        updateGenerated(id, d, hash(n.content()));
        return byId(id);
    }

    /** 1단계(강연 나누기) + 2단계(강연마다 요약, 병렬). 강연 필기가 없으면 빈 목록. */
    private List<Draft> analyze(NoteRow n, boolean forced) {
        List<Found> found = split(n, forced);
        if (found.isEmpty()) {
            if (!forced) return List.of();
            found = List.of(new Found(null, null, null));
        }
        List<String> tags = allTagNames();
        int total = found.size();
        if (total == 1) return List.of(summarizeOne(n, found.get(0), 1, tags));
        // 강연 4개를 한 번에 쓰면 응답이 길어 클라이언트 읽기 제한(90초)에 걸린다 → 강연마다 따로, 동시에.
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.min(total, 4));
        try {
            List<java.util.concurrent.Future<Draft>> futures = new ArrayList<>();
            for (Found f : found) futures.add(pool.submit(() -> summarizeOne(n, f, total, tags)));
            List<Draft> out = new ArrayList<>();
            for (var f : futures) {
                try {
                    out.add(f.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Found> split(NoteRow n, boolean forced) {
        StringBuilder user = new StringBuilder("날짜: ").append(n.date()).append('\n');
        if (forced) user.append("관리자가 이 메모에 강연 필기가 있다고 지정했다. 적어도 하나는 찾는다.\n");
        user.append("\n# 메모").append(n.fileName() == null ? "" : " · " + n.fileName()).append('\n').append(n.content()).append('\n');
        String raw = claude.chat(SPLIT_PROMPT, List.of(new ClaudeChatClient.Message("user", user.toString())), 1500)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다."));
        List<Found> out = new ArrayList<>();
        for (JsonNode l : DiaryService.parseJson(objectMapper, raw).path("lectures")) {
            if (out.size() >= MAX_LECTURES_PER_NOTE) break;
            out.add(new Found(text(l, "speaker"), text(l, "title"), text(l, "cue")));
        }
        return out;
    }

    private Draft summarizeOne(NoteRow n, Found target, int total, List<String> existingTags) {
        String raw = claude.chat(SUMMARY_PROMPT, List.of(new ClaudeChatClient.Message("user", summaryUserPrompt(n, target, total, existingTags))), 4000)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다."));
        JsonNode out = DiaryService.parseJson(objectMapper, raw);
        String content = removeEmptyThoughts(out.path("content").asText("").strip());
        if (content.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 가 강연 요약을 쓰지 못했습니다.");
        String title = Optional.ofNullable(text(out, "title")).orElse(Optional.ofNullable(target.title()).orElse(n.date() + " 강연"));
        String speaker = Optional.ofNullable(text(out, "speaker")).orElse(target.speaker());
        List<String> tags = new ArrayList<>();
        for (JsonNode t : out.path("tags")) tags.add(t.asText());
        List<String> warnings = new ArrayList<>();
        for (JsonNode w : out.path("warnings")) warnings.add(w.asText());
        return new Draft(title, speaker, content, tags.size() > AI_MAX_TAGS ? tags.subList(0, AI_MAX_TAGS) : tags, warnings);
    }

    /**
     * 메모 하나의 요약들을 새 결과로 한꺼번에 갱신한다. 짝(연사→제목→순서)이 고친 초안·게시본이면 그대로 두고,
     * 손대지 않은 초안은 새 내용으로 바꾸고, 짝이 없는 새 강연은 추가, 짝이 없는 손대지 않은 초안은 치운다.
     * @return 새로 만들거나 바꾼 요약 수
     */
    private int sync(NoteRow n, String hash, List<Draft> drafts) {
        List<Lecture> existing = byNote(n.id());
        Map<Integer, Integer> pairs = match(existing.stream().map(e -> new Key(e.speaker(), e.title())).toList(),
                drafts.stream().map(d -> new Key(d.speaker(), d.title())).toList());
        int nextSeq = jdbc.queryForObject("SELECT COALESCE(MAX(seq), -1) + 1 FROM lecture_entry WHERE source_note_id = :n",
                new MapSqlParameterSource("n", n.id()), Integer.class);
        int changed = 0;
        for (int i = 0; i < drafts.size(); i++) {
            Integer j = pairs.get(i);
            if (j != null) {
                Lecture e = existing.get(j);
                if (isProtected(e)) continue;
                updateGenerated(e.id(), drafts.get(i), hash);
            } else {
                insert(n, nextSeq++, drafts.get(i), hash);
            }
            changed++;
        }
        Set<Integer> paired = new HashSet<>(pairs.values());
        for (int j = 0; j < existing.size(); j++) {
            if (!paired.contains(j) && !isProtected(existing.get(j))) deleteEntry(existing.get(j).id());
        }
        saveScan(n.id(), hash, !drafts.isEmpty() || existing.stream().anyMatch(LectureService::isProtected));
        return changed;
    }

    /**
     * 새 요약(drafts) i → 기존 요약(existing) j 짝. 1) 연사가 같으면 2) 제목이 같으면 3) 남은 것끼리 순서대로 —
     * 단 3)은 한쪽 연사가 비어 있을 때만(연사가 다른 강연을 엉뚱하게 덮지 않게). 공백·대소문자는 무시한다.
     */
    static Map<Integer, Integer> match(List<Key> existing, List<Key> drafts) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < drafts.size(); i++) {
                if (out.containsKey(i)) continue;
                for (int j = 0; j < existing.size(); j++) {
                    if (used.contains(j)) continue;
                    Key d = drafts.get(i), e = existing.get(j);
                    boolean ok = switch (pass) {
                        case 0 -> !norm(d.speaker()).isEmpty() && norm(d.speaker()).equals(norm(e.speaker()));
                        case 1 -> !norm(d.title()).isEmpty() && norm(d.title()).equals(norm(e.title()));
                        default -> norm(d.speaker()).isEmpty() || norm(e.speaker()).isEmpty();
                    };
                    if (ok) {
                        out.put(i, j);
                        used.add(j);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isProtected(Lecture e) {
        return e.edited() || "published".equals(e.status());
    }

    private void insert(NoteRow n, int seq, Draft d, String hash) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO lecture_entry (source_note_id, seq, lecture_date, title, speaker, content, warnings, status, input_hash, edited, generated_at)
                VALUES (:n, :seq, :d, :title, :sp, :c, :w, 'draft', :h, 0, :t)
                """, draftParams(d, hash).addValue("n", n.id()).addValue("seq", seq).addValue("d", n.date()), key, new String[]{"id"});
        replaceTags(key.getKey().longValue(), d.tags());
    }

    private void updateGenerated(long id, Draft d, String hash) {
        jdbc.update("""
                UPDATE lecture_entry SET title = :title, speaker = :sp, content = :c, warnings = :w, input_hash = :h,
                    edited = 0, generated_at = :t WHERE id = :id
                """, draftParams(d, hash).addValue("id", id));
        replaceTags(id, d.tags());
    }

    private MapSqlParameterSource draftParams(Draft d, String hash) {
        return new MapSqlParameterSource("title", cut(d.title(), 255))
                .addValue("sp", d.speaker() == null || d.speaker().isBlank() ? null : cut(d.speaker().strip(), 255))
                .addValue("c", d.content()).addValue("w", toJson(d.warnings())).addValue("h", hash).addValue("t", now());
    }

    private void deleteEntry(long id) {
        jdbc.update("DELETE FROM lecture_entry_tag WHERE entry_id = :id", new MapSqlParameterSource("id", id));
        jdbc.update("DELETE FROM lecture_entry WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private NoteRow note(long noteId) {
        return jdbc.query("SELECT id, note_date, file_name, content FROM diary_note WHERE id = :id",
                        new MapSqlParameterSource("id", noteId), (rs, i) -> new NoteRow(rs.getLong("id"),
                                rs.getDate("note_date").toLocalDate(), rs.getString("file_name"), rs.getString("content")))
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메모를 찾을 수 없습니다."));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText().strip() : null;
    }

    // ── 고치기·버리기 ────────────────────────────────

    public Lecture update(long id, String title, String speaker, String content) {
        Lecture e = byId(id);
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "게시한 글은 블로그에서 고쳐 주세요.");
        if (title == null || title.isBlank() || content == null || content.isBlank()) throw bad("제목과 내용을 입력해 주세요.");
        jdbc.update("UPDATE lecture_entry SET title = :t, speaker = :sp, content = :c, edited = 1 WHERE id = :id",
                new MapSqlParameterSource("t", cut(title.strip(), 255))
                        .addValue("sp", speaker == null || speaker.isBlank() ? null : cut(speaker.strip(), 255))
                        .addValue("c", content.strip()).addValue("id", id));
        return byId(id);
    }

    /** 태그를 통째로 바꾼다. 게시한 요약도 태그는 고칠 수 있다(목록 거르기용 메타데이터). 초안이면 '고친 초안'이 된다. */
    public Lecture setTags(long id, List<String> tags) {
        Lecture e = byId(id);
        replaceTags(id, tags == null ? List.of() : tags);
        if (!"published".equals(e.status())) {
            jdbc.update("UPDATE lecture_entry SET edited = 1 WHERE id = :id", new MapSqlParameterSource("id", id));
        }
        return byId(id);
    }

    public void discard(long id) {
        Lecture e = byId(id);
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "게시한 글은 지울 수 없습니다.");
        jdbc.update("DELETE FROM lecture_entry_tag WHERE entry_id = :id", new MapSqlParameterSource("id", id));
        // 자료 파일 목록도 지운다. NAS 의 파일(올린 사본)은 그대로 둔다.
        jdbc.update("DELETE FROM lecture_file WHERE entry_id = :id", new MapSqlParameterSource("id", id));
        jdbc.update("DELETE FROM lecture_entry WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    // ── 직접 기록 ────────────────────────────────────

    /** 강연 직접 기록. 강연명 필수, 날짜가 비면 오늘. 요약(content)은 [요약 만들기] 전까지 비어 있다. */
    public Lecture createManual(Info info) {
        Info in = checkInfo(info);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO lecture_entry (source_note_id, seq, origin, lecture_date, lecture_time, title, speaker, venue, host, series,
                                           books, memo, content, warnings, status, edited, generated_at)
                VALUES (NULL, 0, 'manual', :d, :tm, :title, :sp, :venue, :host, :series, :books, :memo, '', '[]', 'draft', 0, :t)
                """, infoParams(in).addValue("t", now()), key, new String[]{"id"});
        return byId(key.getKey().longValue());
    }

    /**
     * 강연 정보 칸 고치기(직접 기록·메모에서 나온 요약 둘 다). 게시한 글은 블로그 본문이 바뀌지 않으므로 막는다.
     * 메모에서 나온 초안은 제목·연사를 사람이 고친 것이므로 '고친 초안'이 되어 배치가 덮어쓰지 않는다.
     */
    public Lecture updateInfo(long id, Info info) {
        Lecture e = byId(id);
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "게시한 글은 블로그에서 고쳐 주세요.");
        Info in = checkInfo(info);
        jdbc.update("""
                UPDATE lecture_entry SET lecture_date = :d, lecture_time = :tm, title = :title, speaker = :sp, venue = :venue,
                    host = :host, series = :series, books = :books, memo = :memo, edited = IF(origin = 'manual', edited, 1)
                WHERE id = :id
                """, infoParams(in).addValue("id", id));
        return byId(id);
    }

    private Info checkInfo(Info info) {
        if (info == null || !notBlank(info.title())) throw bad("강연명을 입력해 주세요.");
        String date = notBlank(info.date()) ? info.date().strip() : LocalDate.now(DailyAnalysisService.KST).toString();
        try {
            LocalDate.parse(date);
        } catch (Exception ex) {
            throw bad("날짜 형식이 올바르지 않습니다: " + date);
        }
        List<String> books = info.books() == null ? List.of() : info.books().stream().filter(LectureService::notBlank)
                .map(String::strip).distinct().limit(30).toList();
        return new Info(info.title().strip(), info.speaker(), date, info.time(), info.venue(), info.host(), info.series(), books, info.memo());
    }

    private MapSqlParameterSource infoParams(Info in) {
        return new MapSqlParameterSource("d", LocalDate.parse(in.date())).addValue("tm", opt(in.time(), 32))
                .addValue("title", cut(in.title(), 255)).addValue("sp", opt(in.speaker(), 255))
                .addValue("venue", opt(in.venue(), 255)).addValue("host", opt(in.host(), 255)).addValue("series", opt(in.series(), 255))
                .addValue("books", in.books().isEmpty() ? null : toJson(in.books()))
                .addValue("memo", notBlank(in.memo()) ? in.memo().strip() : null);
    }

    private static String opt(String s, int max) {
        return notBlank(s) ? cut(s.strip(), max) : null;
    }

    /** 요약 근거로 쓰는 자료 글 상한(파일 하나, 전체). 넘으면 잘라서 넣고 warnings 에 남긴다. */
    static final int MAX_SOURCE_CHARS_PER_FILE = 120_000;
    static final int MAX_SOURCE_CHARS_TOTAL = 200_000;

    /**
     * 직접 기록의 [요약 만들기]: 내 메모 + 텍스트 자료(받아쓰기, PDF 글)를 근거로 요약 초안을 만든다(메모 요약과 같은 형식).
     * 녹음은 받아쓰기가 끝난 것만 쓴다. 근거가 하나도 없으면 무엇을 해야 하는지 알려 준다.
     */
    public Lecture summarizeManual(long id) {
        Lecture e = byId(id);
        if (!"manual".equals(e.origin())) throw bad("일기 메모에서 나온 요약은 [다시 만들기]를 써 주세요.");
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 게시한 강연 요약입니다.");
        record Src(String name, String kind, String status, String text) {}
        List<Src> files = jdbc.query("SELECT file_name, kind, text_status, extracted_text FROM lecture_file WHERE entry_id = :id ORDER BY id",
                new MapSqlParameterSource("id", id), (rs, i) -> new Src(rs.getString("file_name"), rs.getString("kind"),
                        rs.getString("text_status"), rs.getString("extracted_text")));
        List<Src> texts = files.stream().filter(f -> "ready".equals(f.status()) && notBlank(f.text())).toList();
        List<Src> audioWithoutText = files.stream().filter(f -> "audio".equals(f.kind()) && !"ready".equals(f.status())).toList();
        if (texts.isEmpty() && !notBlank(e.memo())) {
            if (audioWithoutText.stream().anyMatch(f -> "transcribing".equals(f.status()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "녹음을 받아쓰는 중입니다. 끝나면 다시 [요약 만들기]를 눌러 주세요.");
            }
            if (!audioWithoutText.isEmpty()) {
                throw bad(stt.isAvailable() ? "녹음 파일의 [받아쓰기]를 먼저 눌러 주세요."
                        : "STT 서버가 꺼져 있어 녹음을 받아쓸 수 없습니다. 받아쓰기 파일(.txt/.md)을 올려 주세요.");
            }
            throw bad("요약할 근거가 없습니다. 내 메모를 적거나 받아쓰기(.txt/.md)·PDF 자료를 올려 주세요.");
        }

        List<String> warnings = new ArrayList<>();
        StringBuilder src = new StringBuilder();
        int budget = MAX_SOURCE_CHARS_TOTAL;
        for (Src f : texts) {
            String t = f.text().strip();
            int limit = Math.min(MAX_SOURCE_CHARS_PER_FILE, Math.max(0, budget));
            if (limit == 0) {
                warnings.add("자료가 너무 길어 '" + f.name() + "'은(는) 요약에 쓰지 않았습니다.");
                continue;
            }
            if (t.length() > limit) {
                warnings.add("'" + f.name() + "'이(가) 길어 앞부분 " + limit + "자만 썼습니다.");
                t = t.substring(0, limit);
            }
            budget -= t.length();
            src.append("\n## 자료: ").append(f.name()).append(" (").append(kindLabel(f.kind())).append(")\n").append(t).append('\n');
        }
        for (Src f : audioWithoutText) warnings.add("녹음 '" + f.name() + "'은(는) 받아쓰지 않아 요약에 쓰지 않았습니다.");

        StringBuilder user = new StringBuilder();
        user.append("# 강연 정보 (관리자가 직접 적음 — 그대로 믿는다)\n").append(infoLines(e));
        user.append("\n# 기존 태그\n");
        List<String> tags = allTagNames();
        user.append(tags.isEmpty() ? "(아직 없음)" : String.join(", ", tags)).append('\n');
        boolean hasMemo = notBlank(e.memo());
        if (hasMemo) {
            user.append("\n# 내 메모 (관리자가 직접 쓴 글 — '내 생각' 절의 근거)\n").append(e.memo().strip()).append('\n');
        } else {
            // "(없음)" 같은 자리표시를 넘기면 AI 가 '## 내 생각 / (메모 없음)' 을 써 버린다 → 칸 자체를 빼고 쓰지 말라고 못박는다.
            user.append("\n# 내 메모가 없다. '## 내 생각' 절은 쓰지 않는다(제목도, 빈 칸 표시도 쓰지 않는다).\n");
        }
        user.append("\n# 강연 자료 (받아쓰기·강연 자료 글 — 연사가 한 말의 근거)").append(src.isEmpty() ? "\n(없음)\n" : src);

        String raw = claude.chat(MANUAL_PROMPT, List.of(new ClaudeChatClient.Message("user", user.toString())), 4000)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다."));
        JsonNode out = DiaryService.parseJson(objectMapper, raw);
        String content = out.path("content").asText("").strip();
        // 메모가 없으면 AI 가 그래도 쓴 '내 생각' 절은 통째로 뺀다(내 생각은 메모에서만 나온다). 메모가 있으면 빈 자리표시 절만 뺀다.
        content = hasMemo ? removeEmptyThoughts(content) : removeThoughts(content);
        if (content.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 가 강연 요약을 쓰지 못했습니다.");
        for (JsonNode w : out.path("warnings")) warnings.add(w.asText());
        List<String> newTags = new ArrayList<>(e.tags());   // 직접 붙인 태그는 남기고 AI 제안을 더한다
        int added = 0;
        for (JsonNode t : out.path("tags")) if (added++ < AI_MAX_TAGS) newTags.add(t.asText());

        // 강연명·연사는 사람이 적은 값을 그대로 둔다. 연사 칸이 비었을 때만 AI 가 찾은 연사를 넣는다.
        String speaker = notBlank(e.speaker()) ? e.speaker() : text(out, "speaker");
        jdbc.update("""
                UPDATE lecture_entry SET content = :c, speaker = :sp, warnings = :w, edited = 0, generated_at = :t WHERE id = :id
                """, new MapSqlParameterSource("c", content).addValue("sp", opt(speaker, 255))
                .addValue("w", toJson(warnings)).addValue("t", now()).addValue("id", id));
        replaceTags(id, newTags);
        return byId(id);
    }

    private static String infoLines(Lecture e) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 강연명: ").append(e.title()).append('\n');
        if (notBlank(e.speaker())) sb.append("- 연사: ").append(e.speaker()).append('\n');
        sb.append("- 일시: ").append(e.lectureDate()).append(notBlank(e.lectureTime()) ? " " + e.lectureTime() : "").append('\n');
        if (notBlank(e.venue())) sb.append("- 장소: ").append(e.venue()).append('\n');
        if (notBlank(e.host())) sb.append("- 주최: ").append(e.host()).append('\n');
        if (notBlank(e.series())) sb.append("- 시리즈·회차: ").append(e.series()).append('\n');
        if (e.books() != null && !e.books().isEmpty()) sb.append("- 관련 책: ").append(String.join(", ", e.books())).append('\n');
        return sb.toString();
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "audio" -> "녹음 받아쓰기";
            case "pdf" -> "강연 자료 PDF 글";
            default -> "글 파일";
        };
    }

    /** 원본 메모를 지웠을 때. 손대지 않은 초안은 같이 치우고, 고쳤거나 게시한 요약은 남긴다(메모 연결만 끊음). */
    public void onNoteDeleted(long noteId) {
        MapSqlParameterSource p = new MapSqlParameterSource("n", noteId);
        jdbc.update("DELETE FROM lecture_entry_tag WHERE entry_id IN (SELECT id FROM lecture_entry WHERE source_note_id = :n AND status = 'draft' AND edited = 0)", p);
        jdbc.update("DELETE FROM lecture_entry WHERE source_note_id = :n AND status = 'draft' AND edited = 0", p);
        jdbc.update("UPDATE lecture_entry SET source_note_id = NULL WHERE source_note_id = :n", p);
        jdbc.update("DELETE FROM lecture_note_scan WHERE note_id = :n", p);
    }

    // ── 게시 ────────────────────────────────────────

    /** 게시 기본 소메뉴. 아직 없으면 null(게시할 때 만든다). */
    public Long existingLectureSubId(List<DiaryService.SubMenu> humanSubs) {
        return humanSubs.stream().filter(s -> "강연".equals(s.name())).findFirst()
                .or(() -> humanSubs.stream().filter(s -> "강연".equals(s.category())).findFirst())
                .map(DiaryService.SubMenu::id).orElse(null);
    }

    /**
     * 휴먼 블로그에 올린다. subId 가 없으면 '강연' 소메뉴(없으면 '일상' 아래 새로 만든다).
     */
    public Lecture publish(long id, Long subId, String visibility, List<DiaryService.SubMenu> humanSubs) {
        Lecture e = byId(id);
        if ("published".equals(e.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 게시했습니다.");
        if (!notBlank(e.content())) throw bad("요약이 없습니다. [요약 만들기]를 누르거나 [수정]으로 내용을 적어 주세요.");
        long sub = subId != null ? subId : lectureSubId(humanSubs);
        if (subId != null && humanSubs.stream().noneMatch(s -> s.id() == sub)) throw bad("휴먼 페르소나의 소메뉴가 아닙니다.");
        String vis = "private".equals(visibility) ? "private" : "public";

        LocalDateTime t = now();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        // 글 날짜는 게시한 때가 아니라 강연을 들은 때. 게시 시각은 lecture_entry.published_at 에 남는다
        jdbc.update("""
                INSERT INTO journal_post (sub_id, title, content, visibility, created_at, updated_at)
                VALUES (:s, :title, :c, :v, :pt, :t)
                """, new MapSqlParameterSource("s", sub).addValue("title", e.title())
                .addValue("c", DiaryMarkdown.toHtml(postMarkdown(e))).addValue("v", vis).addValue("t", t)
                .addValue("pt", postTime(e.lectureDate(), e.lectureTime())), key, new String[]{"id"});
        jdbc.update("UPDATE lecture_entry SET status = 'published', post_id = :p, published_at = :t WHERE id = :id",
                new MapSqlParameterSource("p", key.getKey().longValue()).addValue("t", t).addValue("id", id));
        return byId(id);
    }

    /** 게시 본문: 맨 위에 강연 정보(연사·일시·장소·주최·시리즈·관련 책)를 목록으로 붙인다. 빈 칸은 뺀다. */
    static String postMarkdown(Lecture e) {
        List<String> rows = new ArrayList<>();
        if (notBlank(e.speaker())) rows.add("- **연사**: " + e.speaker().strip());
        rows.add("- **일시**: " + e.lectureDate() + (notBlank(e.lectureTime()) ? " " + e.lectureTime().strip() : ""));
        if (notBlank(e.venue())) rows.add("- **장소**: " + e.venue().strip());
        if (notBlank(e.host())) rows.add("- **주최**: " + e.host().strip());
        if (notBlank(e.series())) rows.add("- **시리즈**: " + e.series().strip());
        List<String> books = e.books() == null ? List.of() : e.books().stream().filter(LectureService::notBlank)
                .map(s -> s.strip().startsWith("『") ? s.strip() : "『" + s.strip() + "』").toList();
        if (!books.isEmpty()) rows.add("- **관련 책**: " + String.join(", ", books));
        // 예전에 저장된 요약에 '## 내 생각 / (메모 없음)' 같은 빈 절이 있으면 다시 요약하지 않아도 게시본에서는 뺀다.
        return String.join("\n", rows) + "\n\n" + removeEmptyThoughts(e.content());
    }

    /**
     * 이미 게시한 강연을 지금 요약·강연 정보로 블로그 글을 덮어쓴다(소메뉴·공개 여부는 그대로).
     * 게시 형식을 고친 뒤 예전 게시본을 바로잡는 용도. 글 날짜(created_at)도 강연 일시로 다시 맞춘다.
     */
    public Lecture republish(long id) {
        Lecture e = byId(id);
        if (!"published".equals(e.status()) || e.postId() == null) throw bad("아직 게시하지 않은 강연입니다. [게시]를 눌러 주세요.");
        if (!notBlank(e.content())) throw bad("요약이 없습니다.");
        // 강연 일시를 고친 뒤 다시 게시하면 글 날짜도 따라간다(updated_at 은 실제 수정 시각)
        int n = jdbc.update("UPDATE journal_post SET title = :title, content = :c, created_at = :pt, updated_at = :t WHERE id = :p",
                new MapSqlParameterSource("title", e.title()).addValue("c", DiaryMarkdown.toHtml(postMarkdown(e)))
                        .addValue("pt", postTime(e.lectureDate(), e.lectureTime()))
                        .addValue("t", now()).addValue("p", e.postId()));
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "블로그 글(" + e.postId() + ")이 지워져 다시 게시할 수 없습니다.");
        return byId(id);
    }

    private static final java.util.regex.Pattern THOUGHTS_HEADING = java.util.regex.Pattern.compile("^#{1,6}\\s*내\\s*생각\\s*$");
    /** '(메모 없음)', '없음', '- 해당 없음', '(필기에 없음)' 처럼 내용 없이 빈 칸만 알리는 줄. */
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile(
            "^[(\\[]?\\s*(내\\s*)?(메모|필기|생각|내용)?\\s*(에|가|이|는)?\\s*(없음|없습니다|없다|해당\\s*없음|n/?a|-|—)\\s*\\.?\\s*[)\\]]?\\.?$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** '## 내 생각' 절의 본문이 비었거나 자리표시뿐이면 그 절(제목 포함)을 뺀다. 내용이 있으면 그대로 둔다. */
    static String removeEmptyThoughts(String md) {
        return dropThoughts(md, false);
    }

    /** '## 내 생각' 절을 내용과 상관없이 뺀다(내 메모가 없을 때 — 내 생각은 메모에서만 나온다). */
    static String removeThoughts(String md) {
        return dropThoughts(md, true);
    }

    private static String dropThoughts(String md, boolean always) {
        if (md == null || md.isEmpty()) return md;
        String[] lines = md.replace("\r\n", "\n").split("\n", -1);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.length; ) {
            if (THOUGHTS_HEADING.matcher(lines[i].strip()).matches()) {
                int j = i + 1;
                while (j < lines.length && !lines[j].strip().startsWith("#")) j++;
                if (always || isPlaceholder(Arrays.asList(lines).subList(i + 1, j))) {
                    i = j;
                    continue;
                }
            }
            out.add(lines[i++]);
        }
        return String.join("\n", out).strip();
    }

    private static boolean isPlaceholder(List<String> body) {
        for (String raw : body) {
            String s = raw.strip().replaceFirst("^([-*>]|\\d+\\.)\\s*", "").strip();
            if (!s.isEmpty() && !PLACEHOLDER.matcher(s).matches()) return false;
        }
        return true;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private long lectureSubId(List<DiaryService.SubMenu> humanSubs) {
        Long existing = existingLectureSubId(humanSubs);
        if (existing != null) return existing;
        List<Long> cat = jdbc.queryForList("SELECT id FROM journal_category WHERE persona = 'human' AND name = '일상' ORDER BY id LIMIT 1",
                new MapSqlParameterSource(), Long.class);
        if (cat.isEmpty()) throw bad("휴먼 페르소나에 '강연' 소메뉴나 '일상' 카테고리가 없습니다. 소메뉴를 골라 주세요.");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO journal_sub_menu (category_id, name, sort_order)
                SELECT :c, '강연', COALESCE(MAX(sort_order), 0) + 1 FROM journal_sub_menu WHERE category_id = :c
                """, new MapSqlParameterSource("c", cat.get(0)), key, new String[]{"id"});
        log.info("휴먼 '일상 > 강연' 소메뉴 생성");
        return key.getKey().longValue();
    }

    // ── 태그 ────────────────────────────────────────

    /** '#AI ', '  인간 다움 ' → 'AI', '인간 다움'. 비면 null. */
    static String normalizeTag(String raw) {
        if (raw == null) return null;
        String s = raw.strip().replaceAll("^#+", "").replaceAll("\\s+", " ").replaceAll("[,\\p{Cntrl}]", "").strip();
        if (s.isEmpty()) return null;
        return s.length() > MAX_TAG_LENGTH ? s.substring(0, MAX_TAG_LENGTH).strip() : s;
    }

    /** 정규화하고 대소문자만 다른 중복을 뺀다(먼저 나온 표기 유지). */
    static List<String> normalizeTags(List<String> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String r : raw) {
            String t = normalizeTag(r);
            if (t != null) out.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
            if (out.size() >= MAX_TAGS) break;
        }
        return new ArrayList<>(out.values());
    }

    private void replaceTags(long entryId, List<String> raw) {
        MapSqlParameterSource e = new MapSqlParameterSource("e", entryId);
        jdbc.update("DELETE FROM lecture_entry_tag WHERE entry_id = :e", e);
        for (String name : normalizeTags(raw)) {
            jdbc.update("INSERT IGNORE INTO lecture_tag (name, created_at) VALUES (:n, :t)",
                    new MapSqlParameterSource("n", name).addValue("t", now()));
            // 콜레이션이 대소문자를 구분하지 않아 'ai' 로 넣어도 기존 'AI' 태그를 찾는다.
            Long tagId = jdbc.queryForObject("SELECT id FROM lecture_tag WHERE name = :n ORDER BY id LIMIT 1",
                    new MapSqlParameterSource("n", name), Long.class);
            jdbc.update("INSERT IGNORE INTO lecture_entry_tag (entry_id, tag_id) VALUES (:e, :t)",
                    new MapSqlParameterSource("e", entryId).addValue("t", tagId));
        }
    }

    private List<String> allTagNames() {
        return jdbc.queryForList("""
                SELECT t.name FROM lecture_tag t LEFT JOIN lecture_entry_tag et ON et.tag_id = t.id
                GROUP BY t.id, t.name ORDER BY COUNT(et.entry_id) DESC, t.name LIMIT 300
                """, new MapSqlParameterSource(), String.class);
    }

    // ── 프롬프트 ────────────────────────────────────

    /** 1단계: 메모에 강연 필기가 있는지, 몇 개인지. */
    private static final String SPLIT_PROMPT = """
            너는 일기 메모에서 강연 필기를 찾는 도우미다. 입력은 하루 일기 메모 하나다.
            - 강연·강의·세미나·발표·북토크 등을 들으며 연사가 한 말을 적은 필기(주장, 사례, 인용, 목차, 추천 도서)가 있으면 강연 필기다.
            - 일상 메모에서 "강연을 들었다"고 한두 줄 언급만 한 것은 강연 필기가 아니다. 책을 읽은 기록도 아니다.
            - 메모 하나에 여러 연사의 강연이 들어 있을 수 있다(예: 카톡 '나에게 보내기' 필기 — 시각과 연사 이름으로 구분).
              연사(강연)마다 하나씩, 메모에 나온 순서대로 나눈다. 같은 연사의 필기가 떨어져 있어도 한 강연이다.
              필기가 한 줄뿐인 강연(예: 추천 도서 한 줄)도 따로 센다.
            - 연사·제목은 메모에 적힌 대로만. 없으면 null.

            # 출력 형식
            JSON 하나만 출력한다. 코드 블록 표시나 설명은 붙이지 않는다.
            {"lectures": [{"speaker": "연사 이름 또는 null", "title": "강연 제목 또는 null",
                           "cue": "메모에서 이 강연 부분을 찾을 단서(시각, 첫 줄 일부 등) 짧게"}]}
            강연 필기가 없으면 {"lectures": []}
            """;

    /** 직접 기록한 강연 요약: 강연 정보 + 내 메모 + 자료(받아쓰기·PDF 글). 출력 형식은 메모 요약과 같다. */
    private static final String MANUAL_PROMPT = """
            너는 강연 기록을 정리하는 도우미다. 입력은 관리자가 직접 적은 강연 정보, 내 메모, 강연 자료(녹음 받아쓰기, 강연 자료 PDF 의 글)다.
            - 근거에 있는 내용만 쓴다. 연사 경력, 강연 배경, 근거에 없는 주장·예시는 지어내지 않는다. 모르면 그 절을 뺀다.
            - 받아쓰기는 음성 인식 결과라 오타·잘못 들은 말이 있을 수 있다. 문맥으로 분명한 것만 바로잡고, 애매한 고유명사는 warnings 에 적는다.
            - 강연 정보(강연명·연사·일시·장소 등)는 본문에 다시 쓰지 않는다(게시할 때 따로 붙는다).
            - content 는 아래 절로 쓰고, 근거에 내용이 없는 절은 통째로 뺀다.
              ## 핵심 주장 — 2~4문장
              ## 구조 — 강연 흐름을 번호 목록 목차로
              ## 인상 깊은 문장 — 받아쓰기·자료·메모에 있는 문장을 그대로 인용(> 인용). 받아쓰기에서 고르면 말투만 다듬는다
              ## 내 생각 — "내 메모"에 내 생각·느낌이 있을 때만. 받아쓰기·자료로 내 생각을 지어내지 않는다.
                내 메모가 없으면 이 절은 제목까지 통째로 쓰지 않는다("(메모 없음)" 같은 빈 칸 표시도 쓰지 않는다)
            - speaker: 강연 정보에 연사가 없을 때만 근거에서 찾은 연사 이름, 없으면 null.
            - tags: 3~7개. 주제·분야·키워드(예: AI, 인간다움, 자기결정이론). 연사 이름과 강연 제목은 태그로 쓰지 않는다.
              "기존 태그"에 뜻이 같은 태그가 있으면 표기를 그대로 재사용한다. 짧은 명사형, '#' 없이.
            - warnings: 게시 전 확인할 점(실명 언급, 받아쓰기에서 불확실한 고유명사 등). 없으면 빈 배열.

            # 출력 형식
            JSON 하나만 출력한다. 코드 블록 표시나 설명은 붙이지 않는다. 문자열 안의 줄바꿈은 \\n 으로 이스케이프한다.
            {"speaker": "..." 또는 null, "content": "마크다운(제목 줄 없이)", "tags": ["..."], "warnings": ["..."]}
            """;

    /** 2단계: 강연 하나 요약. */
    private static final String SUMMARY_PROMPT = """
                너는 강연 필기를 정리하는 도우미다. 입력은 하루 일기 메모 하나와 그 안에서 요약할 "대상 강연"이다.
                - 메모에 강연이 여러 개 있으면 대상 강연 부분만 요약한다. 다른 연사의 강연 내용을 섞지 않는다.

                # 요약
                - 필기에 있는 내용만 쓴다. 연사 경력, 강연 배경, 필기에 없는 주장·예시는 지어내지 않는다. 모르면 비우거나 그 절을 뺀다.
                - content 는 아래 절로 쓰고, 필기에 내용이 없는 절은 통째로 뺀다.
                  ## 핵심 주장 — 2~4문장
                  ## 구조 — 강연 흐름을 번호 목록 목차로
                  ## 인상 깊은 문장 — 필기에 적힌 문장을 그대로 인용(> 인용)
                  ## 내 생각 — 필기에 내 생각·느낌이 적혀 있을 때만. 없으면 제목까지 이 절을 쓰지 않는다("(없음)" 같은 빈 칸 표시도 쓰지 않는다)
                - title: 강연 제목. 필기에 제목이 없으면 핵심 주제로 짧게 짓고 warnings 에 "제목은 추정"이라고 적는다.
                - speaker: 연사 이름(여럿이면 쉼표로). 필기에 없으면 null.
                - tags: 3~7개. 주제·분야·키워드(예: AI, 인간다움, 자기결정이론). 연사 이름과 강연 제목은 태그로 쓰지 않는다.
                  아래 "기존 태그"에 뜻이 같은 태그가 있으면 표기를 그대로 재사용한다. 짧은 명사형, '#' 없이.
                - warnings: 게시 전 확인할 점(실명 언급, 회사 내부 정보, 추정한 제목·연사 등). 없으면 빈 배열.

                # 출력 형식
                JSON 하나만 출력한다. 코드 블록 표시나 설명은 붙이지 않는다. 문자열 안의 줄바꿈은 \\n 으로 이스케이프한다.
                {"title": "...", "speaker": "..." 또는 null, "content": "마크다운(제목 줄 없이)", "tags": ["..."], "warnings": ["..."]}
                """;

    private static String summaryUserPrompt(NoteRow n, Found target, int total, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        sb.append("날짜: ").append(n.date()).append('\n');
        sb.append("\n# 대상 강연");
        if (total > 1) sb.append(" (이 메모의 강연 ").append(total).append("개 중 하나)");
        sb.append('\n');
        if (target.speaker() == null && target.title() == null && target.cue() == null) {
            sb.append("메모 속 강연 필기 전체\n");
        } else {
            if (target.speaker() != null) sb.append("- 연사: ").append(target.speaker()).append('\n');
            if (target.title() != null) sb.append("- 제목: ").append(target.title()).append('\n');
            if (target.cue() != null) sb.append("- 단서: ").append(target.cue()).append('\n');
        }
        sb.append("\n# 기존 태그\n").append(tags.isEmpty() ? "(아직 없음)" : String.join(", ", tags)).append('\n');
        sb.append("\n# 메모").append(n.fileName() == null ? "" : " · " + n.fileName()).append('\n').append(n.content()).append('\n');
        return sb.toString();
    }

    // ── 내부 ────────────────────────────────────────

    private List<Lecture> query(String whereOrderBy, MapSqlParameterSource p) {
        List<Lecture> rows = jdbc.query("""
                SELECT e.*, n.file_name AS note_file_name FROM lecture_entry e
                LEFT JOIN diary_note n ON n.id = e.source_note_id
                """ + whereOrderBy, p, this::map);
        if (rows.isEmpty()) return rows;
        Map<Long, List<String>> tags = new HashMap<>();
        jdbc.query("""
                SELECT et.entry_id, t.name FROM lecture_entry_tag et JOIN lecture_tag t ON t.id = et.tag_id
                WHERE et.entry_id IN (:ids) ORDER BY t.name
                """, new MapSqlParameterSource("ids", rows.stream().map(Lecture::id).toList()),
                rs -> { tags.computeIfAbsent(rs.getLong("entry_id"), k -> new ArrayList<>()).add(rs.getString("name")); });
        Map<Long, List<LectureFile>> files = new HashMap<>();
        jdbc.query("""
                SELECT id, entry_id, kind, file_name, size_bytes, text_status, text_note, CHAR_LENGTH(extracted_text) AS chars, created_at
                FROM lecture_file WHERE entry_id IN (:ids) ORDER BY id
                """, new MapSqlParameterSource("ids", rows.stream().map(Lecture::id).toList()),
                rs -> { files.computeIfAbsent(rs.getLong("entry_id"), k -> new ArrayList<>()).add(mapFile(rs)); });
        return rows.stream().map(l -> l.with(tags.getOrDefault(l.id(), List.of()), files.getOrDefault(l.id(), List.of()))).toList();
    }

    static LectureFile mapFile(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LectureFile(rs.getLong("id"), rs.getString("kind"), rs.getString("file_name"), rs.getLong("size_bytes"),
                rs.getString("text_status"), rs.getString("text_note"), rs.getInt("chars"),
                rs.getTimestamp("created_at").toLocalDateTime().toString());
    }

    private List<Lecture> byNote(long noteId) {
        return query("WHERE e.source_note_id = :n ORDER BY e.seq, e.id", new MapSqlParameterSource("n", noteId));
    }

    private Lecture map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        List<String> warnings = List.of();
        String w = rs.getString("warnings");
        if (w != null && !w.isBlank()) {
            try {
                warnings = objectMapper.readValue(w, new TypeReference<>() {});
            } catch (Exception ignored) {
                warnings = List.of(w);
            }
        }
        long noteId = rs.getLong("source_note_id");
        Long note = rs.wasNull() ? null : noteId;
        long postId = rs.getLong("post_id");
        Long post = rs.wasNull() ? null : postId;
        java.sql.Timestamp pub = rs.getTimestamp("published_at");
        List<String> books = List.of();
        String b = rs.getString("books");
        if (b != null && !b.isBlank()) {
            try {
                books = objectMapper.readValue(b, new TypeReference<>() {});
            } catch (Exception ignored) {
                books = List.of(b);
            }
        }
        return new Lecture(rs.getLong("id"), note, rs.getString("note_file_name"), rs.getString("origin"),
                rs.getDate("lecture_date").toLocalDate().toString(), rs.getString("lecture_time"),
                rs.getString("title"), rs.getString("speaker"), rs.getString("venue"), rs.getString("host"), rs.getString("series"),
                books, rs.getString("memo"), rs.getString("content"), List.of(), warnings,
                rs.getString("status"), rs.getBoolean("edited"), post,
                rs.getTimestamp("generated_at").toLocalDateTime().toString(), pub == null ? null : pub.toLocalDateTime().toString(),
                List.of());
    }

    private String scanHash(long noteId) {
        List<String> l = jdbc.queryForList("SELECT input_hash FROM lecture_note_scan WHERE note_id = :n",
                new MapSqlParameterSource("n", noteId), String.class);
        return l.isEmpty() ? null : l.get(0);
    }

    private void saveScan(long noteId, String hash, boolean isLecture) {
        jdbc.update("""
                INSERT INTO lecture_note_scan (note_id, input_hash, is_lecture, scanned_at) VALUES (:n, :h, :l, :t)
                ON DUPLICATE KEY UPDATE input_hash = VALUES(input_hash), is_lecture = VALUES(is_lecture), scanned_at = VALUES(scanned_at)
                """, new MapSqlParameterSource("n", noteId).addValue("h", hash).addValue("l", isLecture).addValue("t", now()));
    }

    private static String label(NoteRow n) {
        return n.fileName() != null ? n.fileName() : "메모 " + n.id();
    }

    private static String reason(Exception e) {
        return e instanceof ResponseStatusException r && r.getReason() != null ? r.getReason() : e.getMessage();
    }

    static String hash(String content) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static final java.util.regex.Pattern LEADING_TIME = java.util.regex.Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})");

    /** 블로그 글 날짜: 강연 날짜 + 시작 시각("19:00", "19:00~21:00" 등 앞의 HH:mm). 시각이 없거나 못 읽으면 정오. */
    static LocalDateTime postTime(String lectureDate, String lectureTime) {
        LocalDate d = LocalDate.parse(lectureDate);
        if (lectureTime != null) {
            java.util.regex.Matcher m = LEADING_TIME.matcher(lectureTime);
            if (m.find()) {
                int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
                if (h < 24 && min < 60) return d.atTime(h, min);
            }
        }
        return d.atTime(12, 0);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(DailyAnalysisService.KST).withNano(0);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}

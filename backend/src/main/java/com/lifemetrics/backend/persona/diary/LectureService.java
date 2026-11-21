package com.lifemetrics.backend.persona.diary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.client.ClaudeChatClient;
import com.lifemetrics.backend.service.DailyAnalysisService;
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
 * 휴먼 일기: 강연 요약. 일기 메모(diary_note) 하나에 강연 필기가 있으면 그 메모로 강연 요약 초안(lecture_entry)을 만든다.
 * 강연 하나 = 메모 하나(source_note_id 1:1). 원본 메모·NAS 파일은 고치지 않는다.
 * <p>
 * 메모마다 내용 해시로 판별 결과(lecture_note_scan)를 남겨, 내용이 그대로면 AI 를 다시 부르지 않는다.
 * 사람이 고친 초안과 게시한 요약은 다시 만들지 않는다(일기와 같은 규칙). [버리기]한 요약도 메모가 바뀌기 전엔 되살리지 않는다.
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

    public record Lecture(long id, Long sourceNoteId, String noteFileName, String lectureDate, String title, String speaker,
                          String content, List<String> tags, List<String> warnings, String status, boolean edited,
                          Long postId, String generatedAt, String publishedAt) {}
    public record ScanResult(int scanned, int created, List<String> errors) {}
    public record TagCount(String name, int count) {}
    public record SpeakerCount(String name, int count) {}
    public record Facets(List<TagCount> tags, List<SpeakerCount> speakers) {}

    private record NoteRow(long id, LocalDate date, String fileName, String content) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ClaudeChatClient claude;
    private final ObjectMapper objectMapper;

    public LectureService(@Qualifier("journalDataSource") DataSource dataSource, ClaudeChatClient claude, ObjectMapper objectMapper) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.claude = claude;
        this.objectMapper = objectMapper;
    }

    // ── 조회 ────────────────────────────────────────

    public List<Lecture> forDate(LocalDate date) {
        return query("WHERE e.lecture_date = :d ORDER BY e.id", new MapSqlParameterSource("d", date));
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
            Optional<Lecture> current = byNote(n.id());
            if (current.isPresent() && (current.get().edited() || "published".equals(current.get().status()))) {
                saveScan(n.id(), hash, true);
                continue;
            }
            if (n.content().strip().length() < MIN_LECTURE_CHARS) {
                saveScan(n.id(), hash, false);
                continue;
            }
            try {
                scanned++;
                if (summarize(n, hash, false)) created++;
            } catch (Exception e) {
                log.warn("강연 요약 실패 (메모 {}): {}", n.id(), e.getMessage());
                errors.add(label(n) + ": " + reason(e));
            }
        }
        if (scanned > 0) log.info("강연 판별 {} — 메모 {}건 확인, 요약 {}건", date, scanned, created);
        return new ScanResult(scanned, created, errors);
    }

    /** 메모 하나를 강연 필기로 보고 요약을 (다시) 만든다. 사람이 고친 초안도 덮어쓴다(화면에서 확인을 받는다). */
    public Lecture summarizeNote(long noteId) {
        NoteRow n = jdbc.query("SELECT id, note_date, file_name, content FROM diary_note WHERE id = :id",
                        new MapSqlParameterSource("id", noteId), (rs, i) -> new NoteRow(rs.getLong("id"),
                                rs.getDate("note_date").toLocalDate(), rs.getString("file_name"), rs.getString("content")))
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메모를 찾을 수 없습니다."));
        Optional<Lecture> current = byNote(noteId);
        if (current.isPresent() && "published".equals(current.get().status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 게시한 강연 요약입니다.");
        }
        summarize(n, hash(n.content()), true);
        return byNote(noteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 가 강연 요약을 쓰지 못했습니다."));
    }

    /** @return 요약을 만들었으면 true, 강연 필기가 아니면 false */
    private boolean summarize(NoteRow n, String hash, boolean forced) {
        String raw = claude.chat(systemPrompt(), List.of(new ClaudeChatClient.Message("user", userPrompt(n, forced))), 6000)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다."));
        JsonNode out = DiaryService.parseJson(objectMapper, raw);
        boolean isLecture = forced || out.path("lecture").asBoolean(false);
        String content = out.path("content").asText("").strip();
        if (isLecture && content.isEmpty()) {
            if (forced) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 가 강연 요약을 쓰지 못했습니다.");
            isLecture = false;
        }
        if (!isLecture) {
            saveScan(n.id(), hash, false);
            // 내용이 바뀌어 더는 강연 필기가 아니면, 손대지 않은 예전 초안은 치운다.
            jdbc.update("DELETE FROM lecture_entry_tag WHERE entry_id IN (SELECT id FROM lecture_entry WHERE source_note_id = :n AND status = 'draft' AND edited = 0)",
                    new MapSqlParameterSource("n", n.id()));
            jdbc.update("DELETE FROM lecture_entry WHERE source_note_id = :n AND status = 'draft' AND edited = 0",
                    new MapSqlParameterSource("n", n.id()));
            return false;
        }
        List<String> warnings = new ArrayList<>();
        for (JsonNode w : out.path("warnings")) warnings.add(w.asText());
        String title = out.path("title").asText("").strip();
        if (title.isEmpty()) title = n.date() + " 강연";
        String speaker = out.path("speaker").isTextual() ? out.path("speaker").asText().strip() : null;
        List<String> tags = new ArrayList<>();
        for (JsonNode t : out.path("tags")) tags.add(t.asText());

        long id = upsert(n, title, speaker, content, warnings, hash);
        replaceTags(id, tags.size() > AI_MAX_TAGS ? tags.subList(0, AI_MAX_TAGS) : tags);
        saveScan(n.id(), hash, true);
        return true;
    }

    private long upsert(NoteRow n, String title, String speaker, String content, List<String> warnings, String hash) {
        MapSqlParameterSource p = new MapSqlParameterSource("n", n.id()).addValue("d", n.date())
                .addValue("title", cut(title, 255)).addValue("sp", speaker == null || speaker.isEmpty() ? null : cut(speaker, 255))
                .addValue("c", content).addValue("w", toJson(warnings)).addValue("h", hash).addValue("t", now());
        jdbc.update("""
                INSERT INTO lecture_entry (source_note_id, lecture_date, title, speaker, content, warnings, status, input_hash, edited, generated_at)
                VALUES (:n, :d, :title, :sp, :c, :w, 'draft', :h, 0, :t)
                ON DUPLICATE KEY UPDATE title = VALUES(title), speaker = VALUES(speaker), content = VALUES(content),
                    warnings = VALUES(warnings), input_hash = VALUES(input_hash), edited = 0, generated_at = VALUES(generated_at)
                """, p);
        return jdbc.queryForObject("SELECT id FROM lecture_entry WHERE source_note_id = :n", p, Long.class);
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
        jdbc.update("DELETE FROM lecture_entry WHERE id = :id", new MapSqlParameterSource("id", id));
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
        long sub = subId != null ? subId : lectureSubId(humanSubs);
        if (subId != null && humanSubs.stream().noneMatch(s -> s.id() == sub)) throw bad("휴먼 페르소나의 소메뉴가 아닙니다.");
        String vis = "private".equals(visibility) ? "private" : "public";

        LocalDateTime t = now();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO journal_post (sub_id, title, content, visibility, created_at, updated_at)
                VALUES (:s, :title, :c, :v, :t, :t)
                """, new MapSqlParameterSource("s", sub).addValue("title", e.title())
                .addValue("c", DiaryMarkdown.toHtml(postMarkdown(e))).addValue("v", vis).addValue("t", t), key, new String[]{"id"});
        jdbc.update("UPDATE lecture_entry SET status = 'published', post_id = :p, published_at = :t WHERE id = :id",
                new MapSqlParameterSource("p", key.getKey().longValue()).addValue("t", t).addValue("id", id));
        return byId(id);
    }

    /** 게시 본문: 맨 위에 연사·날짜 한 줄을 붙인다. */
    static String postMarkdown(Lecture e) {
        String head = (e.speaker() == null || e.speaker().isBlank() ? "" : "연사: " + e.speaker() + " · ") + e.lectureDate();
        return "*" + head + "*\n\n" + e.content();
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

    private static String systemPrompt() {
        return """
                너는 강연 필기를 정리하는 도우미다. 입력은 하루 일기 메모 하나다.

                # 1. 강연 필기인지 판단
                - 강연·강의·세미나·발표·북토크 등을 들으며 연사가 한 말을 적은 필기(주장, 사례, 인용, 목차)가 있으면 강연 필기다.
                - 일상 메모에서 "강연을 들었다"고 한두 줄 언급만 한 것은 강연 필기가 아니다. 책을 읽은 기록도 아니다.
                - 메모 하나에는 강연 하나만 있다고 본다. 섞여 있으면 강연 부분만 요약한다.

                # 2. 요약 (강연 필기일 때만)
                - 필기에 있는 내용만 쓴다. 연사 경력, 강연 배경, 필기에 없는 주장·예시는 지어내지 않는다. 모르면 비우거나 그 절을 뺀다.
                - content 는 아래 절로 쓰고, 필기에 내용이 없는 절은 통째로 뺀다.
                  ## 핵심 주장 — 2~4문장
                  ## 구조 — 강연 흐름을 번호 목록 목차로
                  ## 인상 깊은 문장 — 필기에 적힌 문장을 그대로 인용(> 인용)
                  ## 내 생각 — 필기에 내 생각·느낌이 적혀 있을 때만. 없으면 이 절을 쓰지 않는다
                - title: 강연 제목. 필기에 제목이 없으면 핵심 주제로 짧게 짓고 warnings 에 "제목은 추정"이라고 적는다.
                - speaker: 연사 이름(여럿이면 쉼표로). 필기에 없으면 null.
                - tags: 3~7개. 주제·분야·키워드(예: AI, 인간다움, 자기결정이론). 연사 이름과 강연 제목은 태그로 쓰지 않는다.
                  아래 "기존 태그"에 뜻이 같은 태그가 있으면 표기를 그대로 재사용한다. 짧은 명사형, '#' 없이.
                - warnings: 게시 전 확인할 점(실명 언급, 회사 내부 정보, 추정한 제목·연사 등). 없으면 빈 배열.

                # 출력 형식
                JSON 하나만 출력한다. 코드 블록 표시나 설명은 붙이지 않는다. 문자열 안의 줄바꿈은 \\n 으로 이스케이프한다.
                강연 필기가 아니면: {"lecture": false}
                강연 필기면: {"lecture": true, "title": "...", "speaker": "..." 또는 null, "content": "마크다운(제목 줄 없이)",
                             "tags": ["..."], "warnings": ["..."]}
                """;
    }

    private String userPrompt(NoteRow n, boolean forced) {
        StringBuilder sb = new StringBuilder();
        sb.append("날짜: ").append(n.date()).append('\n');
        if (forced) sb.append("관리자가 이 메모를 강연 필기라고 지정했다. lecture 는 true 로 하고 요약을 쓴다.\n");
        List<String> tags = allTagNames();
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
        return rows.stream().map(l -> new Lecture(l.id(), l.sourceNoteId(), l.noteFileName(), l.lectureDate(), l.title(),
                l.speaker(), l.content(), tags.getOrDefault(l.id(), List.of()), l.warnings(), l.status(), l.edited(),
                l.postId(), l.generatedAt(), l.publishedAt())).toList();
    }

    private Optional<Lecture> byNote(long noteId) {
        return query("WHERE e.source_note_id = :n", new MapSqlParameterSource("n", noteId)).stream().findFirst();
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
        return new Lecture(rs.getLong("id"), note, rs.getString("note_file_name"), rs.getDate("lecture_date").toLocalDate().toString(),
                rs.getString("title"), rs.getString("speaker"), rs.getString("content"), List.of(), warnings,
                rs.getString("status"), rs.getBoolean("edited"), post,
                rs.getTimestamp("generated_at").toLocalDateTime().toString(), pub == null ? null : pub.toLocalDateTime().toString());
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

    private static LocalDateTime now() {
        return LocalDateTime.now(DailyAnalysisService.KST).withNano(0);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}

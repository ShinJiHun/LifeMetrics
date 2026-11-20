package com.lifemetrics.backend.persona.worklog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 업로드한 일일·주간보고 JSON 을 보고서 단위로 풀어 journal_db.work_log_report 에 저장.
 * 형식(사용자 report.py 원본): {description, daily:[...]} 또는 {description, weekly:[...]}.
 * 최상위 배열이나 보고서 하나({type, date|period, ...})도 받는다. 같은 (kind, 날짜)는 덮어쓴다.
 */
@Service
public class WorkLogReportService {

    private static final Pattern FULL_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})-(\\d{1,2})");

    public record ParseResult(int inserted, int updated, int unchanged, List<String> skipped, int entriesSynced) {}
    public record ReportSummary(String kind, String reportDate, String periodEnd, String category, String title,
                                int taskCount, String updatedAt) {}
    public record ReportDetail(String kind, String reportDate, String periodEnd, String category, String title,
                               String author, JsonNode summary, JsonNode plan, JsonNode details, Long uploadId,
                               String updatedAt) {}
    public record TopicTask(String date, String task, String content, String status) {}
    public record TopicGroup(String topic, int taskCount, int dayCount, String firstDate, String lastDate,
                             List<TopicTask> tasks) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final WorkLogService workLogService;

    public WorkLogReportService(@Qualifier("journalDataSource") DataSource dataSource, ObjectMapper objectMapper,
                                WorkLogService workLogService) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.workLogService = workLogService;
    }

    // ── 파싱 ──

    @Transactional(transactionManager = "journalTransactionManager")
    public ParseResult parse(long uploadId, String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 형식이 아닙니다: " + e.getOriginalMessage());
        }
        List<JsonNode[]> items = new ArrayList<>(); // [kindHint(text node or null), item]
        collect(root, null, items);

        int ins = 0, upd = 0, same = 0, synced = 0;
        List<String> skipped = new ArrayList<>();
        for (JsonNode[] pair : items) {
            JsonNode item = pair[1];
            String kind = kindOf(item, pair[0] == null ? null : pair[0].asText());
            String label = text(item, "name") != null ? text(item, "name") : shorten(item.toString());
            if (kind == null) { skipped.add(label + ": daily/weekly 구분 불가"); continue; }

            LocalDate start, end = null;
            if (kind.equals("daily")) {
                start = firstDate(text(item, "date"));
                if (start == null) { skipped.add(label + ": 날짜(date) 없음"); continue; }
            } else {
                String period = text(item, "period");
                start = firstDate(period != null ? period : text(item, "weekStart"));
                if (start == null) { skipped.add(label + ": 기간(period) 없음"); continue; }
                end = periodEnd(period, start);
            }
            int r = upsert(kind, start, end, item, uploadId);
            switch (r) {
                case 1 -> ins++;
                case 2 -> upd++;
                default -> same++;
            }
            // 업무 일지(일자별)·주간보고 칸에도 반영 — 보고서가 바뀌었거나 아직 비어 있을 때만 (손으로 고친 내용 보호)
            if (syncEntry(kind, start, item, r != 0)) synced++;
        }
        return new ParseResult(ins, upd, same, skipped, synced);
    }

    private void collect(JsonNode node, String kindHint, List<JsonNode[]> out) {
        if (node == null) return;
        if (node.isArray()) {
            for (JsonNode n : node) collect(n, kindHint, out);
            return;
        }
        if (!node.isObject()) return;
        boolean container = false;
        for (String k : List.of("daily", "weekly")) {
            if (node.path(k).isArray()) { container = true; collect(node.get(k), k, out); }
        }
        if (!container && (node.has("summary") || node.has("details") || node.has("date") || node.has("period"))) {
            out.add(new JsonNode[]{kindHint == null ? null : objectMapper.getNodeFactory().textNode(kindHint), node});
        }
    }

    private static String kindOf(JsonNode item, String hint) {
        String t = text(item, "type");
        if ("daily".equalsIgnoreCase(t) || "weekly".equalsIgnoreCase(t)) return t.toLowerCase(Locale.ROOT);
        if (hint != null) return hint;
        if (item.has("period")) return "weekly";
        if (item.has("date")) return "daily";
        return null;
    }

    /** "2026-10-22 (목)", "2026-10-22" → 2026-10-22 */
    static LocalDate firstDate(String s) {
        if (s == null) return null;
        Matcher m = FULL_DATE.matcher(s);
        if (!m.find()) return null;
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "2026-10-19 ~ 10-23" / "2026-12-28 ~ 2027-01-01" → 종료일. 연도가 없으면 시작일 연도(넘어가면 +1). */
    static LocalDate periodEnd(String period, LocalDate start) {
        if (period == null) return null;
        int sep = period.indexOf('~');
        if (sep < 0) return null;
        String tail = period.substring(sep + 1);
        LocalDate full = firstDate(tail);
        if (full != null) return full;
        Matcher m = MONTH_DAY.matcher(tail);
        if (!m.find()) return null;
        try {
            LocalDate d = LocalDate.of(start.getYear(), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            return d.isBefore(start) ? d.plusYears(1) : d;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return 1 새로 추가, 2 변경, 0 동일 */
    private int upsert(String kind, LocalDate date, LocalDate end, JsonNode item, long uploadId) {
        String raw = item.toString();
        MapSqlParameterSource p = new MapSqlParameterSource("k", kind).addValue("d", date);
        List<String> existing = jdbc.queryForList(
                "SELECT raw_json FROM work_log_report WHERE kind = :k AND report_date = :d", p, String.class);
        if (!existing.isEmpty() && existing.get(0).equals(raw)) return 0;

        LocalDateTime now = LocalDateTime.now().withNano(0); // DB 기본값은 UTC → 앱(Asia/Seoul) 시각으로
        p.addValue("e", end)
                .addValue("c", cut(text(item, "category"), 255))
                .addValue("t", cut(text(item, "title"), 255))
                .addValue("a", cut(text(item, "author"), 100))
                .addValue("s", json(item.get("summary")))
                .addValue("pl", json(item.get("plan")))
                .addValue("dt", json(item.get("details")))
                .addValue("r", raw)
                .addValue("u", uploadId)
                .addValue("now", now);
        if (existing.isEmpty()) {
            jdbc.update("""
                    INSERT INTO work_log_report (kind, report_date, period_end, category, title, author,
                        summary_json, plan_json, details_json, raw_json, upload_id, created_at, updated_at)
                    VALUES (:k, :d, :e, :c, :t, :a, :s, :pl, :dt, :r, :u, :now, :now)
                    """, p);
            return 1;
        }
        jdbc.update("""
                UPDATE work_log_report SET period_end = :e, category = :c, title = :t, author = :a,
                    summary_json = :s, plan_json = :pl, details_json = :dt, raw_json = :r, upload_id = :u, updated_at = :now
                WHERE kind = :k AND report_date = :d
                """, p);
        return 2;
    }

    // ── 업무 일지 반영 ──

    /** 저장된 보고서 전체를 업무 일지에 다시 반영 (비어 있는 날만, force 면 덮어씀). */
    @Transactional(transactionManager = "journalTransactionManager")
    public int syncAllEntries(boolean force) {
        int[] n = {0};
        jdbc.query("SELECT kind, report_date, raw_json FROM work_log_report ORDER BY report_date",
                new MapSqlParameterSource(), rs -> {
                    JsonNode item = node(rs.getString("raw_json"));
                    if (item != null && syncEntry(rs.getString("kind"), rs.getDate("report_date").toLocalDate(), item, force)) n[0]++;
                });
        return n[0];
    }

    private boolean syncEntry(String kind, LocalDate date, JsonNode item, boolean overwrite) {
        if (kind.equals("daily")) {
            if (!overwrite && exists("SELECT COUNT(*) FROM work_log_entry WHERE log_date = :d", date)) return false;
            Sections s = sections(item);
            workLogService.save(date, s.done(), s.plan(), s.issues());
        } else {
            LocalDate ws = WorkLogService.mondayOf(date);
            if (!overwrite && exists("SELECT COUNT(*) FROM work_log_weekly WHERE week_start = :d", ws)) return false;
            Sections s = sections(item);
            workLogService.saveWeekly(ws, s.done(), s.plan(), s.issues());
        }
        return true;
    }

    private boolean exists(String sql, LocalDate d) {
        Integer c = jdbc.queryForObject(sql, new MapSqlParameterSource("d", d), Integer.class);
        return c != null && c > 0;
    }

    record Sections(String done, String plan, String issues) {}

    private static final Pattern PLAN_HEADING = Pattern.compile("다음\\s*작업|명일|계획|예정 작업|할 일");
    private static final Pattern ISSUE_HEADING = Pattern.compile("이슈|확인\\s*필요|미해결|문의|리스크|보류");

    /**
     * 보고서 JSON → 금일 실적 / 명일 계획 / 이슈 텍스트.
     * summary[{task,content,status}] 와 plan[] 은 각각 실적·계획, details 는 제목(heading)으로 나눈다.
     */
    static Sections sections(JsonNode item) {
        StringBuilder done = new StringBuilder(), plan = new StringBuilder(), issues = new StringBuilder();
        for (JsonNode t : iter(item.get("summary"))) {
            String line = taskLine(t);
            if (line != null) done.append("- ").append(line).append('\n');
        }
        for (JsonNode t : iter(item.get("plan"))) {
            String line = taskLine(t);
            if (line != null) plan.append("- ").append(line).append('\n');
        }
        for (JsonNode d : iter(item.get("details"))) {
            String heading = text(d, "heading");
            List<String> lines = new ArrayList<>();
            for (JsonNode it : iter(d.get("items"))) {
                String v = it.isTextual() ? it.asText().trim() : it.toString();
                if (!v.isEmpty()) lines.add(v);
            }
            if (heading == null && lines.isEmpty()) continue;
            boolean isPlan = heading != null && PLAN_HEADING.matcher(heading).find();
            boolean isIssue = !isPlan && heading != null && ISSUE_HEADING.matcher(heading).find();
            if (isPlan) {
                for (String l : lines) plan.append("- ").append(l).append('\n');
            } else if (isIssue) {
                for (String l : lines) issues.append("- ").append(l).append('\n');
            } else {
                if (done.length() > 0 && done.charAt(done.length() - 1) == '\n' && !done.toString().endsWith("\n\n")) done.append('\n');
                if (heading != null) done.append("[").append(heading).append("]\n");
                for (String l : lines) done.append("  · ").append(l).append('\n');
            }
        }
        return new Sections(done.toString().strip(), plan.toString().strip(), issues.toString().strip());
    }

    private static String taskLine(JsonNode t) {
        if (t.isTextual()) return t.asText().trim().isEmpty() ? null : t.asText().trim();
        String task = text(t, "task"), content = text(t, "content"), status = text(t, "status");
        if (task == null && content == null) return null;
        StringBuilder b = new StringBuilder();
        if (task != null) b.append(task);
        if (content != null) b.append(task != null ? ": " : "").append(content);
        if (status != null) b.append(" (").append(status).append(")");
        return b.toString();
    }

    private static Iterable<JsonNode> iter(JsonNode n) {
        return n != null && n.isArray() ? n : List.of();
    }

    // ── 조회 ──

    public List<ReportSummary> list(String kind) {
        String where = kind == null || kind.isBlank() ? "" : "WHERE kind = :k";
        return jdbc.query("""
                SELECT kind, report_date, period_end, category, title, summary_json, updated_at
                FROM work_log_report %s ORDER BY report_date DESC, kind
                """.formatted(where), new MapSqlParameterSource("k", kind), (rs, i) -> new ReportSummary(
                rs.getString("kind"), rs.getDate("report_date").toLocalDate().toString(),
                rs.getDate("period_end") == null ? null : rs.getDate("period_end").toLocalDate().toString(),
                rs.getString("category"), rs.getString("title"), size(rs.getString("summary_json")),
                rs.getTimestamp("updated_at").toLocalDateTime().toString()));
    }

    public ReportDetail get(String kind, LocalDate date) {
        List<ReportDetail> l = jdbc.query("""
                SELECT * FROM work_log_report WHERE kind = :k AND report_date = :d
                """, new MapSqlParameterSource("k", kind).addValue("d", date), (rs, i) -> new ReportDetail(
                rs.getString("kind"), rs.getDate("report_date").toLocalDate().toString(),
                rs.getDate("period_end") == null ? null : rs.getDate("period_end").toLocalDate().toString(),
                rs.getString("category"), rs.getString("title"), rs.getString("author"),
                node(rs.getString("summary_json")), node(rs.getString("plan_json")), node(rs.getString("details_json")),
                (Long) rs.getObject("upload_id", Long.class), rs.getTimestamp("updated_at").toLocalDateTime().toString()));
        if (l.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다.");
        return l.get(0);
    }

    /**
     * 일일보고 과제를 큰 주제별로 묶음 — 포트폴리오 초안 재료. 주제는 과제마다 키워드로 정한다({@link WorkLogTopics}).
     */
    public List<TopicGroup> topics() {
        Map<String, List<TopicTask>> byTopic = new HashMap<>();
        jdbc.query("""
                SELECT category, report_date, summary_json FROM work_log_report
                WHERE kind = 'daily' ORDER BY report_date
                """, new MapSqlParameterSource(), rs -> {
            String cat = rs.getString("category");
            String date = rs.getDate("report_date").toLocalDate().toString();
            JsonNode s = node(rs.getString("summary_json"));
            if (s == null || !s.isArray()) return;
            for (JsonNode t : s) {
                String task = text(t, "task"), content = text(t, "content");
                if (task == null && content == null) continue;
                String topic = WorkLogTopics.classify(task, content, cat);
                byTopic.computeIfAbsent(topic, x -> new ArrayList<>())
                        .add(new TopicTask(date, task, content, text(t, "status")));
            }
        });
        List<TopicGroup> out = new ArrayList<>();
        byTopic.forEach((topic, tasks) -> out.add(new TopicGroup(topic, tasks.size(),
                (int) tasks.stream().map(TopicTask::date).distinct().count(),
                tasks.get(0).date(), tasks.get(tasks.size() - 1).date(), tasks)));
        out.sort(Comparator.comparingInt((TopicGroup g) -> WorkLogTopics.order(g.topic())));
        return out;
    }

    // ── util ──

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String cut(String s, int max) { return s == null || s.length() <= max ? s : s.substring(0, max); }

    private static String shorten(String s) { return s.length() > 60 ? s.substring(0, 60) + "…" : s; }

    private String json(JsonNode n) { return n == null || n.isNull() ? null : n.toString(); }

    private JsonNode node(String s) {
        if (s == null) return null;
        try { return objectMapper.readTree(s); } catch (JsonProcessingException e) { return null; }
    }

    private int size(String s) {
        JsonNode n = node(s);
        return n != null && n.isArray() ? n.size() : 0;
    }
}

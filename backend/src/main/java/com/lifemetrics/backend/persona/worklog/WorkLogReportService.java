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

    public record ParseResult(int inserted, int updated, int unchanged, List<String> skipped) {}
    public record ReportSummary(String kind, String reportDate, String periodEnd, String category, String title,
                                int taskCount, String updatedAt) {}
    public record ReportDetail(String kind, String reportDate, String periodEnd, String category, String title,
                               String author, JsonNode summary, JsonNode plan, JsonNode details, Long uploadId,
                               String updatedAt) {}
    public record CategoryGroup(String category, int dailyCount, String firstDate, String lastDate,
                                List<String> tasks) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkLogReportService(@Qualifier("journalDataSource") DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
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

        int ins = 0, upd = 0, same = 0;
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
            switch (upsert(kind, start, end, item, uploadId)) {
                case 1 -> ins++;
                case 2 -> upd++;
                default -> same++;
            }
        }
        return new ParseResult(ins, upd, same, skipped);
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

    /** 일일보고를 업무 분류별로 묶음 — 포트폴리오 초안 재료. 분류가 없으면 "(분류 없음)". */
    public List<CategoryGroup> categories() {
        Map<String, List<Object[]>> byCat = new LinkedHashMap<>();
        jdbc.query("""
                SELECT category, report_date, summary_json FROM work_log_report
                WHERE kind = 'daily' ORDER BY report_date
                """, new MapSqlParameterSource(), rs -> {
            String c = rs.getString("category");
            byCat.computeIfAbsent(c == null || c.isBlank() ? "(분류 없음)" : c, x -> new ArrayList<>())
                    .add(new Object[]{rs.getDate("report_date").toLocalDate().toString(), rs.getString("summary_json")});
        });
        List<CategoryGroup> out = new ArrayList<>();
        byCat.forEach((cat, rows) -> {
            LinkedHashSet<String> tasks = new LinkedHashSet<>();
            for (Object[] r : rows) {
                JsonNode s = node((String) r[1]);
                if (s != null && s.isArray()) for (JsonNode t : s) {
                    String task = text(t, "task");
                    if (task != null) tasks.add(task);
                }
            }
            out.add(new CategoryGroup(cat, rows.size(), (String) rows.get(0)[0], (String) rows.get(rows.size() - 1)[0],
                    new ArrayList<>(tasks)));
        });
        out.sort(Comparator.comparing(CategoryGroup::lastDate).reversed());
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

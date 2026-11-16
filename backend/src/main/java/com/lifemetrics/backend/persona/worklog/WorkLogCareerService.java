package com.lifemetrics.backend.persona.worklog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifemetrics.backend.client.ClaudeChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 저장된 일일·주간보고(work_log_report)로 현재 프로젝트 경력기술서 초안을 만들고, 관리자가 승인하면 career_* 에 반영.
 * 초안은 매번 전체 보고서로 새로 만든다(누적 수정 X). 반영은 항상 사람이 누른다 — 배치는 초안만 만든다.
 */
@Service
public class WorkLogCareerService {

    public record DraftSummary(long id, String status, String triggerType, String sourceFrom, String sourceTo,
                               int reportCount, String createdAt, String appliedAt) {}
    public record Draft(long id, String status, String triggerType, String sourceFrom, String sourceTo,
                        int reportCount, String createdAt, String appliedAt, JsonNode draft,
                        List<String> warnings, JsonNode current) {}

    private static final String SYSTEM = """
            당신은 개발자 경력기술서를 쓰는 편집자다. 사용자가 프리랜서로 일하는 현재 프로젝트의 일일·주간보고를 주제별로 묶어 준다.
            이것으로 경력기술서의 '프로젝트 한 건'을 한국어로 작성한다.

            규칙
            - 보고서에 있는 사실만 쓴다. 성과·수치·역할을 지어내거나 부풀리지 않는다. 수치는 보고서에 있는 것만(테스트 시나리오 건수 등).
            - 보안: 회사 내부 정보는 일반화한다. 서버명·호스트·IP·포트·계정 ID·사번·DB/테이블명·소스 파일명·클래스/메서드명·커밋 해시·
              프로그램 ID·내부 URL·담당자 실명은 쓰지 않는다. (예: "M2X DB(M2X_SUBMIT) 조회" → "문자 발송 시스템 연동 확인")
              공개 기술명(Spring Boot, Vue, Redis, AES, SAP RFC 등)은 써도 된다.
            - 단순 일정 관리·환경 정비·소스 복구 같은 잡무는 빼거나 한 줄로 합친다. 경력에서 의미 있는 개발·설계·테스트·품질 작업 위주.
            - 문체: 기존 경력기술서와 같이 "~했습니다." 체. 과장된 수식어 없이 담백하게.

            출력: JSON 하나만 (설명·코드블록 없이)
            {
              "title": "프로젝트명 (한 줄, 예: '그룹웨어 모바일 앱 로그인·인증 및 게시판 개발')",
              "intro": "프로젝트 소개 1~2문장",
              "summary": "요약 한 줄",
              "stack": "사용 기술, 쉼표 구분 (보고서에서 확인되는 것만)",
              "tasks": ["주요 작업 한 문장씩, 6~10개, 주제별로 묶어서 중요한 순서"]
            }
            """;

    /** 초안에 남은 내부 정보 의심 패턴 (사람이 확인하도록 경고만). */
    private static final List<Map.Entry<String, Pattern>> LEAK_PATTERNS = List.of(
            Map.entry("소스 파일명", Pattern.compile("\\b[\\w-]+\\.(vue|ts|tsx|js|java|yml|yaml|xml|properties|py|sql)\\b")),
            Map.entry("IP 주소", Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")),
            Map.entry("포트/localhost", Pattern.compile("localhost|:\\d{4,5}\\b|포트\\s*\\d{3,5}")),
            Map.entry("커밋 해시", Pattern.compile("\\b[0-9a-f]{7,40}\\b")),
            Map.entry("DB/테이블명", Pattern.compile("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]{2,}\\b")),
            Map.entry("계정 ID", Pattern.compile("\\b[a-z]+\\.\\d{3}\\.\\d{3}\\b")),
            Map.entry("프로그램/테스트 ID", Pattern.compile("\\b[A-Z]{2,4}[-_][A-Z]{0,3}_?\\d{3,4}\\b"))
    );

    /** 파일명 패턴에 걸리지만 공개 기술명인 것. */
    private static final Set<String> TECH_NAMES = Set.of("vue.js", "node.js", "next.js", "nuxt.js", "react.js",
            "express.js", "chart.js", "three.js", "d3.js", "socket.io");

    private final NamedParameterJdbcTemplate jdbc;
    private final ClaudeChatClient claude;
    private final ObjectMapper objectMapper;
    private final WorkLogReportService reportService;
    private final String company;
    private final String careerPath;

    public WorkLogCareerService(@Qualifier("journalDataSource") DataSource dataSource, ClaudeChatClient claude,
                                ObjectMapper objectMapper, WorkLogReportService reportService,
                                @Value("${worklog.company:한화손해보험}") String company,
                                @Value("${worklog.career-path:~/career/hanwha-freelance}") String careerPath) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.claude = claude;
        this.objectMapper = objectMapper;
        this.reportService = reportService;
        this.company = company;
        this.careerPath = careerPath;
    }

    // ── 초안 만들기 ──

    public Draft generate(String trigger) {
        List<WorkLogReportService.TopicGroup> topics = reportService.topics();
        List<WorkLogReportService.ReportSummary> weeklies = reportService.list("weekly");
        List<WorkLogReportService.ReportSummary> dailies = reportService.list("daily");
        if (dailies.isEmpty() && weeklies.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "저장된 보고서가 없습니다. JSON 을 먼저 올려 주세요.");
        }
        String from = earliest(dailies, weeklies), to = latest(dailies, weeklies);

        StringBuilder sb = new StringBuilder("회사: ").append(company).append(" (프리랜서)\n기간: ")
                .append(from).append(" ~ ").append(to).append(" (진행 중)\n\n# 일일보고 과제 (주제별)\n");
        for (WorkLogReportService.TopicGroup g : topics) {
            sb.append("\n## ").append(g.topic()).append(" (").append(g.firstDate()).append(" ~ ").append(g.lastDate())
                    .append(", 과제 ").append(g.taskCount()).append("건)\n");
            for (WorkLogReportService.TopicTask t : g.tasks()) {
                sb.append("- ").append(t.date()).append(" ").append(nz(t.task()));
                if (t.content() != null) sb.append(": ").append(t.content());
                if (t.status() != null) sb.append(" [").append(t.status()).append("]");
                sb.append('\n');
            }
        }
        sb.append("\n# 주간보고\n");
        List<WorkLogReportService.ReportSummary> weeklyAsc = new ArrayList<>(weeklies);
        Collections.reverse(weeklyAsc);
        for (WorkLogReportService.ReportSummary w : weeklyAsc) {
            WorkLogReportService.ReportDetail d = reportService.get("weekly", LocalDate.parse(w.reportDate()));
            sb.append("\n## ").append(w.reportDate()).append(" ~ ").append(nz(w.periodEnd()))
                    .append(" ").append(nz(w.category())).append('\n');
            appendItems(sb, d.summary());
            if (d.details() != null) for (JsonNode det : d.details()) {
                sb.append("  [").append(det.path("heading").asText()).append("]\n");
                for (JsonNode it : det.path("items")) sb.append("  - ").append(it.asText()).append('\n');
            }
        }

        String raw = claude.chat(SYSTEM, List.of(new ClaudeChatClient.Message("user", sb.toString())), 4000)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다."));
        JsonNode p = parseJson(raw);

        ObjectNode project = objectMapper.createObjectNode();
        project.put("title", p.path("title").asText(""));
        project.put("periodLabel", ym(from) + " ~ 진행 중");
        project.put("intro", p.path("intro").asText(""));
        project.put("summary", p.path("summary").asText(""));
        project.put("stack", p.path("stack").asText(""));
        project.set("tasks", p.path("tasks").isArray() ? p.get("tasks") : objectMapper.createArrayNode());

        ObjectNode companyNode = objectMapper.createObjectNode();
        Map<String, Object> existing = existingCompany();
        companyNode.put("companyName", existing != null ? (String) existing.get("company_name") : company);
        companyNode.put("shortName", existing != null ? (String) existing.get("short_name") : company);
        companyNode.put("domain", existing != null ? (String) existing.get("domain") : "금융(보험)");
        companyNode.put("role", existing != null ? (String) existing.get("role") : "프리랜서 · 모바일/웹 개발");
        companyNode.put("startDate", existing != null && existing.get("start_date") != null
                ? existing.get("start_date").toString() : from);
        companyNode.put("stack", p.path("stack").asText(""));

        ObjectNode root = objectMapper.createObjectNode();
        root.set("company", companyNode);
        root.set("project", project);

        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO work_log_career_draft (status, trigger_type, draft_json, source_from, source_to, report_count, created_at)
                VALUES ('draft', :t, :j, :f, :to, :n, :now)
                """, new MapSqlParameterSource("t", trigger).addValue("j", root.toString())
                .addValue("f", LocalDate.parse(from)).addValue("to", LocalDate.parse(to))
                .addValue("n", dailies.size() + weeklies.size()).addValue("now", now()), key, new String[]{"id"});
        return get(key.getKey().longValue());
    }

    /** 배치용: 마지막 초안 이후 보고서가 바뀐 경우에만 새 초안. */
    public Optional<Draft> generateIfChanged() {
        LocalDateTime lastReport = jdbc.queryForObject("SELECT MAX(updated_at) FROM work_log_report",
                new MapSqlParameterSource(), LocalDateTime.class);
        if (lastReport == null) return Optional.empty();
        LocalDateTime lastDraft = jdbc.queryForObject("SELECT MAX(created_at) FROM work_log_career_draft",
                new MapSqlParameterSource(), LocalDateTime.class);
        if (lastDraft != null && !lastReport.isAfter(lastDraft)) return Optional.empty();
        return Optional.of(generate("batch"));
    }

    // ── 조회·수정 ──

    public List<DraftSummary> list() {
        return jdbc.query("""
                SELECT id, status, trigger_type, source_from, source_to, report_count, created_at, applied_at
                FROM work_log_career_draft WHERE status <> 'discarded' ORDER BY id DESC LIMIT 30
                """, new MapSqlParameterSource(), (rs, i) -> new DraftSummary(rs.getLong("id"), rs.getString("status"),
                rs.getString("trigger_type"), str(rs.getDate("source_from")), str(rs.getDate("source_to")),
                rs.getInt("report_count"), rs.getTimestamp("created_at").toLocalDateTime().toString(),
                rs.getTimestamp("applied_at") == null ? null : rs.getTimestamp("applied_at").toLocalDateTime().toString()));
    }

    public Draft get(long id) {
        List<Draft> l = jdbc.query("SELECT * FROM work_log_career_draft WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> {
                    JsonNode d = node(rs.getString("draft_json"));
                    return new Draft(rs.getLong("id"), rs.getString("status"), rs.getString("trigger_type"),
                            str(rs.getDate("source_from")), str(rs.getDate("source_to")), rs.getInt("report_count"),
                            rs.getTimestamp("created_at").toLocalDateTime().toString(),
                            rs.getTimestamp("applied_at") == null ? null : rs.getTimestamp("applied_at").toLocalDateTime().toString(),
                            d, warnings(d), null);
                });
        if (l.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "초안을 찾을 수 없습니다.");
        Draft d = l.get(0);
        return new Draft(d.id(), d.status(), d.triggerType(), d.sourceFrom(), d.sourceTo(), d.reportCount(),
                d.createdAt(), d.appliedAt(), d.draft(), d.warnings(), current());
    }

    public Draft update(long id, JsonNode draft) {
        requireDraft(id);
        if (draft == null || !draft.path("project").isObject() || !draft.path("company").isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "초안 형식이 올바르지 않습니다.");
        }
        jdbc.update("UPDATE work_log_career_draft SET draft_json = :j WHERE id = :id",
                new MapSqlParameterSource("j", draft.toString()).addValue("id", id));
        return get(id);
    }

    public void discard(long id) {
        requireDraft(id);
        jdbc.update("UPDATE work_log_career_draft SET status = 'discarded' WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    // ── 반영 ──

    /** 초안을 career_company / career_project / career_project_task 에 반영. 반영 직전 내용은 backup_json 에. */
    @Transactional(transactionManager = "journalTransactionManager")
    public Draft apply(long id) {
        requireDraft(id);
        JsonNode d = get(id).draft();
        JsonNode c = d.path("company"), p = d.path("project");
        if (p.path("title").asText("").isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "프로젝트명이 비어 있습니다.");
        }
        JsonNode backup = current();
        LocalDate start = parseDate(c.path("startDate").asText(null));

        // 회사
        Map<String, Object> existing = existingCompany();
        MapSqlParameterSource cp = new MapSqlParameterSource("path", careerPath)
                .addValue("dom", blankToNull(c.path("domain").asText("")))
                .addValue("name", c.path("companyName").asText(company))
                .addValue("short", blankToNull(c.path("shortName").asText("")))
                .addValue("period", periodLabel(start))
                .addValue("start", start)
                .addValue("role", c.path("role").asText("프리랜서"))
                .addValue("stack", blankToNull(c.path("stack").asText("")));
        long companyId;
        if (existing == null) {
            Integer minOrder = jdbc.queryForObject("SELECT COALESCE(MIN(sort_order), 1) FROM career_company",
                    new MapSqlParameterSource(), Integer.class);
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO career_company (path, domain, company_name, short_name, period_label, start_date, end_date,
                        role, is_current, stack, sort_order)
                    VALUES (:path, :dom, :name, :short, :period, :start, NULL, :role, 1, :stack, :ord)
                    """, cp.addValue("ord", minOrder - 1), key, new String[]{"id"});
            companyId = key.getKey().longValue();
        } else {
            companyId = ((Number) existing.get("id")).longValue();
            jdbc.update("""
                    UPDATE career_company SET domain = :dom, company_name = :name, short_name = :short,
                        period_label = :period, start_date = :start, role = :role, is_current = 1, stack = :stack
                    WHERE id = :id
                    """, cp.addValue("id", companyId));
        }

        // 프로젝트: 이전에 반영한 프로젝트가 남아 있으면 그걸 갱신
        String paragraphs = "<p>" + esc(p.path("intro").asText("")) + "</p>"
                + "<p><strong>요약:</strong> " + esc(p.path("summary").asText("")) + "</p>"
                + "<p><strong>사용 기술:</strong> " + esc(p.path("stack").asText("")) + "</p>";
        MapSqlParameterSource pp = new MapSqlParameterSource("cid", companyId)
                .addValue("title", p.path("title").asText()).addValue("period", blankToNull(p.path("periodLabel").asText("")))
                .addValue("para", paragraphs);
        Long projectId = linkedProjectId(companyId);
        if (projectId == null) {
            Integer nextOrder = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM career_project WHERE company_id = :cid", pp, Integer.class);
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO career_project (company_id, title, period_label, paragraphs, sort_order)
                    VALUES (:cid, :title, :period, :para, :ord)
                    """, pp.addValue("ord", nextOrder), key, new String[]{"id"});
            projectId = key.getKey().longValue();
        } else {
            jdbc.update("UPDATE career_project SET title = :title, period_label = :period, paragraphs = :para WHERE id = :pid",
                    pp.addValue("pid", projectId));
        }

        // 작업: 사진·영상이 붙지 않은 작업만 교체 (붙은 건 손으로 관리한 것이라 남김)
        MapSqlParameterSource tp = new MapSqlParameterSource("pid", projectId);
        jdbc.update("""
                DELETE FROM career_project_task WHERE project_id = :pid
                  AND id NOT IN (SELECT task_id FROM career_task_media)
                """, tp);
        Integer maxOrder = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), 0) FROM career_project_task WHERE project_id = :pid",
                tp, Integer.class);
        int order = maxOrder;
        for (JsonNode t : p.path("tasks")) {
            String s = t.asText("").trim();
            if (s.isEmpty()) continue;
            jdbc.update("INSERT INTO career_project_task (project_id, description, sort_order) VALUES (:pid, :d, :o)",
                    new MapSqlParameterSource("pid", projectId).addValue("d", "<p>" + esc(s) + "</p>").addValue("o", ++order));
        }

        jdbc.update("""
                UPDATE work_log_career_draft SET status = 'applied', applied_at = :now,
                    applied_company_id = :cid, applied_project_id = :pid, backup_json = :b WHERE id = :id
                """, new MapSqlParameterSource("now", now()).addValue("cid", companyId).addValue("pid", projectId)
                .addValue("b", backup == null ? null : backup.toString()).addValue("id", id));
        return get(id);
    }

    // ── 현재 경력기술서 (비교용) ──

    private JsonNode current() {
        Map<String, Object> co = existingCompany();
        if (co == null) return null;
        ObjectNode n = objectMapper.createObjectNode();
        n.put("companyName", (String) co.get("company_name"));
        n.put("periodLabel", (String) co.get("period_label"));
        n.put("role", (String) co.get("role"));
        long companyId = ((Number) co.get("id")).longValue();
        Long pid = linkedProjectId(companyId);
        if (pid != null) {
            jdbc.query("SELECT title, period_label, paragraphs FROM career_project WHERE id = :id",
                    new MapSqlParameterSource("id", pid), rs -> {
                        n.put("projectId", pid);
                        n.put("title", rs.getString("title"));
                        n.put("projectPeriod", rs.getString("period_label"));
                        n.put("paragraphs", rs.getString("paragraphs"));
                    });
            n.set("tasks", objectMapper.valueToTree(jdbc.queryForList(
                    "SELECT description FROM career_project_task WHERE project_id = :id ORDER BY sort_order",
                    new MapSqlParameterSource("id", pid), String.class)));
        }
        return n;
    }

    private Map<String, Object> existingCompany() {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT * FROM career_company WHERE path = :p",
                new MapSqlParameterSource("p", careerPath));
        return l.isEmpty() ? null : l.get(0);
    }

    /** 마지막으로 반영한 초안의 프로젝트가 아직 이 회사에 있으면 그 id. */
    private Long linkedProjectId(long companyId) {
        List<Long> l = jdbc.queryForList("""
                SELECT d.applied_project_id FROM work_log_career_draft d
                JOIN career_project p ON p.id = d.applied_project_id AND p.company_id = :cid
                WHERE d.status = 'applied' ORDER BY d.applied_at DESC LIMIT 1
                """, new MapSqlParameterSource("cid", companyId), Long.class);
        return l.isEmpty() ? null : l.get(0);
    }

    // ── util ──

    private List<String> warnings(JsonNode d) {
        if (d == null) return List.of();
        StringBuilder all = new StringBuilder();
        JsonNode p = d.path("project");
        for (String f : List.of("title", "intro", "summary", "stack")) all.append(p.path(f).asText("")).append('\n');
        for (JsonNode t : p.path("tasks")) all.append(t.asText("")).append('\n');
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : LEAK_PATTERNS) {
            Matcher m = e.getValue().matcher(all);
            LinkedHashSet<String> hits = new LinkedHashSet<>();
            while (m.find() && hits.size() < 5) {
                if (!TECH_NAMES.contains(m.group().toLowerCase(Locale.ROOT))) hits.add(m.group());
            }
            if (!hits.isEmpty()) out.add(e.getKey() + " 의심: " + String.join(", ", hits));
        }
        return out;
    }

    private void requireDraft(long id) {
        List<String> s = jdbc.queryForList("SELECT status FROM work_log_career_draft WHERE id = :id",
                new MapSqlParameterSource("id", id), String.class);
        if (s.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "초안을 찾을 수 없습니다.");
        if (!"draft".equals(s.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 반영했거나 버린 초안입니다. 새 초안을 만들어 주세요.");
        }
    }

    private static void appendItems(StringBuilder sb, JsonNode arr) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode t : arr) {
            sb.append("- ").append(t.path("task").asText(""));
            if (t.hasNonNull("content")) sb.append(": ").append(t.path("content").asText());
            if (t.hasNonNull("status")) sb.append(" [").append(t.path("status").asText()).append("]");
            sb.append('\n');
        }
    }

    private JsonNode parseJson(String raw) {
        String s = raw.trim();
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답 형식이 올바르지 않습니다.");
        try {
            return objectMapper.readTree(s.substring(a, b + 1));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 해석하지 못했습니다.");
        }
    }

    private JsonNode node(String s) {
        try { return s == null ? null : objectMapper.readTree(s); } catch (JsonProcessingException e) { return null; }
    }

    /** "2026.10 ~ 현재 · 2개월" (기존 경력과 같은 형식, 시작 달 포함). */
    static String periodLabel(LocalDate start) {
        if (start == null) return "진행 중";
        LocalDate today = LocalDate.now();
        long months = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), today.withDayOfMonth(1)) + 1;
        String len = months >= 12 ? (months / 12) + "년" + (months % 12 > 0 ? " " + (months % 12) + "개월" : "") : months + "개월";
        return ym(start.toString()) + " ~ 현재 · " + len;
    }

    private static String ym(String date) { return date.substring(0, 4) + "." + date.substring(5, 7); }

    private static String earliest(List<WorkLogReportService.ReportSummary> a, List<WorkLogReportService.ReportSummary> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).map(WorkLogReportService.ReportSummary::reportDate)
                .min(String::compareTo).orElseThrow();
    }

    private static String latest(List<WorkLogReportService.ReportSummary> a, List<WorkLogReportService.ReportSummary> b) {
        return java.util.stream.Stream.concat(a.stream(),  b.stream())
                .map(r -> r.periodEnd() != null ? r.periodEnd() : r.reportDate()).max(String::compareTo).orElseThrow();
    }

    private static LocalDate parseDate(String s) {
        try { return s == null || s.isBlank() ? null : LocalDate.parse(s.trim()); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시작일 형식은 YYYY-MM-DD 입니다."); }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String str(java.sql.Date d) { return d == null ? null : d.toLocalDate().toString(); }
    private static LocalDateTime now() { return LocalDateTime.now().withNano(0); }
}

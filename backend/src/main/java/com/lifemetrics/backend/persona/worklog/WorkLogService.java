package com.lifemetrics.backend.persona.worklog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.client.ClaudeChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 업무 일지: 채팅으로 적은 내용을 AI 가 금일 실적 / 명일 계획 / 이슈로 정리하고,
 * 일일 기록을 모아 주간보고(주간 실적 / 차주 계획 / 이슈)를 만든다. journal_db 에 저장.
 */
@Service
public class WorkLogService {

    private static final int MAX_HISTORY = 30;   // AI 에 보내는 최근 대화 수

    public record Entry(String logDate, String company, String todayDone, String tomorrowPlan, String issues) {}
    public record Message(String role, String content, String createdAt) {}
    public record Day(Entry entry, List<Message> messages) {}
    public record ChatResult(String reply, Entry draft) {}
    public record Weekly(String weekStart, String doneSummary, String nextPlan, String issues,
                         List<Entry> days) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ClaudeChatClient claude;
    private final ObjectMapper objectMapper;
    private final String company;

    public WorkLogService(@Qualifier("journalDataSource") DataSource dataSource,
                          ClaudeChatClient claude, ObjectMapper objectMapper,
                          @Value("${worklog.company:한화손해보험}") String company) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.claude = claude;
        this.objectMapper = objectMapper;
        this.company = company;
    }

    // ── 조회 ─────────────────────────────────────────

    public List<Entry> list(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT log_date, company, today_done, tomorrow_plan, issues FROM work_log_entry
                WHERE log_date BETWEEN :from AND :to ORDER BY log_date
                """, new MapSqlParameterSource("from", from).addValue("to", to), (rs, i) -> new Entry(
                rs.getDate("log_date").toLocalDate().toString(), rs.getString("company"),
                rs.getString("today_done"), rs.getString("tomorrow_plan"), rs.getString("issues")));
    }

    public Day day(LocalDate date) {
        return new Day(entry(date), messages(date, Integer.MAX_VALUE));
    }

    private Entry entry(LocalDate date) {
        List<Entry> l = list(date, date);
        return l.isEmpty() ? null : l.get(0);
    }

    private List<Message> messages(LocalDate date, int limit) {
        List<Message> all = jdbc.query("""
                SELECT role, content, created_at FROM work_log_message WHERE log_date = :d ORDER BY id
                """, new MapSqlParameterSource("d", date), (rs, i) -> new Message(
                rs.getString("role"), rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime().toString()));
        return all.size() > limit ? all.subList(all.size() - limit, all.size()) : all;
    }

    // ── 저장 ─────────────────────────────────────────

    public Entry save(LocalDate date, String todayDone, String tomorrowPlan, String issues) {
        jdbc.update("""
                INSERT INTO work_log_entry (log_date, company, today_done, tomorrow_plan, issues)
                VALUES (:d, :c, :done, :plan, :issues)
                ON DUPLICATE KEY UPDATE today_done = VALUES(today_done), tomorrow_plan = VALUES(tomorrow_plan),
                                        issues = VALUES(issues)
                """, new MapSqlParameterSource("d", date).addValue("c", company)
                .addValue("done", blankToNull(todayDone)).addValue("plan", blankToNull(tomorrowPlan))
                .addValue("issues", blankToNull(issues)));
        return entry(date);
    }

    private void addMessage(LocalDate date, String role, String content) {
        jdbc.update("INSERT INTO work_log_message (log_date, role, content) VALUES (:d, :r, :c)",
                new MapSqlParameterSource("d", date).addValue("r", role).addValue("c", content));
    }

    // ── 채팅 → 정리 ─────────────────────────────────

    private static final String DAILY_SYSTEM = """
            당신은 사용자의 업무 일지 정리를 돕는 비서입니다. 사용자는 금융사(%s) 보안망에서 일하는 프리랜서 개발자이고,
            퇴근 후 오늘 한 일을 대충 적습니다. 이를 일일보고 형식(금일 실적 / 명일 계획 / 이슈)으로 정리합니다.

            규칙
            - 사용자가 말한 사실만 정리하세요. 수치·결과·원인을 지어내지 마세요.
            - 각 항목은 한 줄에 하나씩, "- " 로 시작하는 짧은 업무 문장(…함/…완료/…예정 같은 보고서체)으로 씁니다.
            - 이전 정리본(current)이 있으면 그걸 바탕으로 새 내용을 합치거나 고치세요. 사용자가 지우라고 한 것만 지웁니다.
            - 이슈가 없으면 issues 는 빈 문자열.
            - 보안: 고객 개인정보, 계좌·주민번호, IP·서버명·계정·비밀번호, 내부 시스템 고유명 등 회사 기밀로 보이는 내용이 있으면
              정리본에서는 일반적인 표현으로 바꾸고("보험금 청구 배치" 정도), reply 에서 그 사실을 짧게 알려주세요.
            - 나중에 경력기술서에 쓸 수 있게, 성과를 보여줄 수치(처리 건수, 단축 시간 등)나 원인·해결 방법이 빠져 있으면
              reply 에서 한 가지만 짧게 물어보세요. 이미 충분하면 묻지 마세요.
            - reply 는 한국어 1~3문장.

            반드시 아래 JSON 하나만 출력하세요 (코드블록 없이):
            {"reply": "...", "todayDone": "- ...\\n- ...", "tomorrowPlan": "- ...", "issues": "- ..."}
            """;

    public ChatResult chat(LocalDate date, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
        }
        if (!claude.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 키가 설정되어 있지 않습니다.");
        }
        Entry current = entry(date);
        List<ClaudeChatClient.Message> msgs = new ArrayList<>();
        for (Message m : messages(date, MAX_HISTORY)) {
            msgs.add(new ClaudeChatClient.Message(m.role(), m.content()));
        }
        String context = "날짜: " + date + " (" + koDow(date) + ")\n"
                + "current(저장된 정리본): " + (current == null ? "없음" : toJson(Map.of(
                "todayDone", nz(current.todayDone()), "tomorrowPlan", nz(current.tomorrowPlan()),
                "issues", nz(current.issues()))));
        msgs.add(new ClaudeChatClient.Message("user", context + "\n\n사용자 입력:\n" + userMessage.trim()));

        String raw = claude.chat(DAILY_SYSTEM.formatted(company), mergeSameRole(msgs), 1500)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다."));
        JsonNode j = parseJson(raw);

        Entry draft = new Entry(date.toString(), company,
                j.path("todayDone").asText(""), j.path("tomorrowPlan").asText(""), j.path("issues").asText(""));
        String reply = j.path("reply").asText("정리했어요. 확인 후 저장해 주세요.");

        // 대화 기록: 사용자 원문과, 다음 턴이 이어받을 수 있게 정리본을 포함한 assistant 응답
        addMessage(date, "user", userMessage.trim());
        addMessage(date, "assistant", reply);
        // 정리본은 바로 저장 (화면에서 고쳐서 다시 저장 가능)
        Entry saved = save(date, draft.todayDone(), draft.tomorrowPlan(), draft.issues());
        return new ChatResult(reply, saved);
    }

    // ── 주간보고 ─────────────────────────────────────

    private static final String WEEKLY_SYSTEM = """
            당신은 프리랜서 개발자의 주간보고를 작성합니다. 입력은 한 주(월~일)의 일일보고입니다.
            - 주간 실적(doneSummary): 일일 금일 실적을 업무 단위로 묶어 중복을 합친 5~10줄. 진행 중이던 일이 완료됐으면 완료로.
            - 차주 계획(nextPlan): 마지막 날들의 명일 계획과 아직 끝나지 않은 일 기준 3~6줄.
            - 이슈(issues): 주중 이슈와 해결 여부. 없으면 빈 문자열.
            - 각 줄은 "- " 로 시작하는 보고서체. 입력에 없는 사실을 지어내지 마세요.
            반드시 JSON 하나만 출력 (코드블록 없이): {"doneSummary": "...", "nextPlan": "...", "issues": "..."}
            """;

    public static LocalDate mondayOf(LocalDate d) {
        return d.with(DayOfWeek.MONDAY);
    }

    public Weekly weekly(LocalDate weekStart) {
        LocalDate ws = mondayOf(weekStart);
        List<Entry> days = list(ws, ws.plusDays(6));
        List<Weekly> rows = jdbc.query("""
                SELECT done_summary, next_plan, issues FROM work_log_weekly WHERE week_start = :w
                """, new MapSqlParameterSource("w", ws), (rs, i) -> new Weekly(ws.toString(),
                rs.getString("done_summary"), rs.getString("next_plan"), rs.getString("issues"), days));
        return rows.isEmpty() ? new Weekly(ws.toString(), null, null, null, days) : rows.get(0);
    }

    public Weekly generateWeekly(LocalDate weekStart) {
        LocalDate ws = mondayOf(weekStart);
        List<Entry> days = list(ws, ws.plusDays(6));
        if (days.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 주에 일일 기록이 없습니다.");
        }
        StringBuilder sb = new StringBuilder("주간: " + ws + " ~ " + ws.plusDays(6) + "\n");
        for (Entry e : days) {
            LocalDate d = LocalDate.parse(e.logDate());
            sb.append("\n[").append(e.logDate()).append(" ").append(koDow(d)).append("]\n")
                    .append("금일 실적:\n").append(nz(e.todayDone())).append("\n")
                    .append("명일 계획:\n").append(nz(e.tomorrowPlan())).append("\n")
                    .append("이슈:\n").append(nz(e.issues())).append("\n");
        }
        String raw = claude.chat(WEEKLY_SYSTEM, List.of(new ClaudeChatClient.Message("user", sb.toString())), 2000)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다."));
        JsonNode j = parseJson(raw);
        return saveWeekly(ws, j.path("doneSummary").asText(""), j.path("nextPlan").asText(""), j.path("issues").asText(""));
    }

    public Weekly saveWeekly(LocalDate weekStart, String done, String next, String issues) {
        LocalDate ws = mondayOf(weekStart);
        jdbc.update("""
                INSERT INTO work_log_weekly (week_start, done_summary, next_plan, issues) VALUES (:w, :d, :n, :i)
                ON DUPLICATE KEY UPDATE done_summary = VALUES(done_summary), next_plan = VALUES(next_plan),
                                        issues = VALUES(issues)
                """, new MapSqlParameterSource("w", ws).addValue("d", blankToNull(done))
                .addValue("n", blankToNull(next)).addValue("i", blankToNull(issues)));
        return weekly(ws);
    }

    // ── 유틸 ─────────────────────────────────────────

    /** Claude 는 user/assistant 가 번갈아야 하므로 같은 역할이 연속되면 합친다. */
    private static List<ClaudeChatClient.Message> mergeSameRole(List<ClaudeChatClient.Message> in) {
        List<ClaudeChatClient.Message> out = new ArrayList<>();
        for (ClaudeChatClient.Message m : in) {
            if (!out.isEmpty() && out.get(out.size() - 1).role().equals(m.role())) {
                ClaudeChatClient.Message last = out.remove(out.size() - 1);
                out.add(new ClaudeChatClient.Message(m.role(), last.content() + "\n\n" + m.content()));
            } else {
                out.add(m);
            }
        }
        // 첫 메시지는 user 여야 한다
        while (!out.isEmpty() && !"user".equals(out.get(0).role())) out.remove(0);
        return out;
    }

    private JsonNode parseJson(String raw) {
        String s = raw.trim();
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답 형식이 올바르지 않습니다.");
        try {
            return objectMapper.readTree(s.substring(a, b + 1));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답 형식이 올바르지 않습니다.");
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String koDow(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> "월"; case TUESDAY -> "화"; case WEDNESDAY -> "수"; case THURSDAY -> "목";
            case FRIDAY -> "금"; case SATURDAY -> "토"; case SUNDAY -> "일";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

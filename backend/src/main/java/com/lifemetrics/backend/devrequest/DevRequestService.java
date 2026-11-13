package com.lifemetrics.backend.devrequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 개발 요청함. 앱에서 받은 요청을 저장하고, 서버 워커(deploy/dev-request-worker.py)가
 * {@link #claim()} 으로 하나씩 가져가 처리한 뒤 {@link #report} 로 결과를 돌려준다.
 * <p>
 * 작업은 한 번에 하나만 돈다(dev 서버가 하나라서). 상태 흐름은 sql/dev_request.sql 참고.
 */
@Service
public class DevRequestService {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String DEV_READY = "DEV_READY";
    public static final String FAILED = "FAILED";
    public static final String APPROVED = "APPROVED";
    public static final String PROMOTING = "PROMOTING";
    public static final String DONE = "DONE";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String SUPERSEDED = "SUPERSEDED";
    /** 코드 변경 없이 답만 한 경우(질문·상의). 빌드·배포 없음. */
    public static final String ANSWERED = "ANSWERED";

    /** 워커가 보고할 수 있는 상태. RUNNING/PROMOTING 은 진행 로그만 갱신(heartbeat). */
    static final Set<String> WORKER_STATUSES = Set.of(RUNNING, PROMOTING, DEV_READY, ANSWERED, FAILED, DONE);
    private static final Set<String> TERMINAL = Set.of(DEV_READY, ANSWERED, FAILED, DONE);
    /** 이 상태인 대화의 마지막 메시지에 이어서 말할 수 있다. */
    static final Set<String> FOLLOWABLE = Set.of(DEV_READY, ANSWERED, FAILED, DONE, REJECTED, CANCELLED);
    /** 이어 말하면 앞 메시지의 승인/반려 버튼이 의미 없어지므로 SUPERSEDED 로 바꾼다. 나머지는 상태를 그대로 둔다. */
    private static final Set<String> SUPERSEDE_ON_FOLLOW = Set.of(DEV_READY, ANSWERED, FAILED);

    /** 이 시간 동안 워커 소식이 없으면 멈춘 것으로 보고 FAILED 처리한다. */
    static final int STALE_MINUTES = 30;

    static final int MAX_TITLE = 200;
    static final int MAX_BODY = 20_000;
    private static final int MAX_LOG = 60_000;

    private final NamedParameterJdbcTemplate jdbc;

    public DevRequestService(@Qualifier("ridingDataSource") DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public record DevRequest(Long id, Long parentId, String title, String body, String status,
                             String branch, String commitSha, String sessionId,
                             String summary, String diffStat, String logTail, String error, BigDecimal costUsd,
                             String createdAt, String startedAt, String finishedAt,
                             String approvedAt, String promotedAt, String updatedAt) {
    }

    /** 워커 보고. null 인 필드는 바꾸지 않는다. */
    public record Report(String status, String branch, String commitSha, String sessionId,
                         String summary, String diffStat, String logTail, String error, BigDecimal costUsd) {
    }

    private static final String COLS = """
            id, parent_id, title, body, status, branch, commit_sha, session_id, summary, diff_stat, %s AS log_tail,
            error, cost_usd, created_at, started_at, finished_at, approved_at, promoted_at, updated_at""";

    private static final RowMapper<DevRequest> MAPPER = (rs, i) -> new DevRequest(
            rs.getLong("id"), (Long) rs.getObject("parent_id", Long.class),
            rs.getString("title"), rs.getString("body"), rs.getString("status"),
            rs.getString("branch"), rs.getString("commit_sha"), rs.getString("session_id"),
            rs.getString("summary"), rs.getString("diff_stat"), rs.getString("log_tail"), rs.getString("error"),
            rs.getBigDecimal("cost_usd"),
            ts(rs, "created_at"), ts(rs, "started_at"), ts(rs, "finished_at"),
            ts(rs, "approved_at"), ts(rs, "promoted_at"), ts(rs, "updated_at"));

    private static String ts(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime().toString();
    }

    /** 목록은 진행 로그를 빼고 돌려준다(크다). */
    public List<DevRequest> list() {
        return jdbc.query("SELECT " + COLS.formatted("NULL") + " FROM dev_request ORDER BY id DESC LIMIT 200",
                MAPPER);
    }

    public Optional<DevRequest> get(long id) {
        return jdbc.query("SELECT " + COLS.formatted("log_tail") + " FROM dev_request WHERE id = :id",
                new MapSqlParameterSource("id", id), MAPPER).stream().findFirst();
    }

    /**
     * 새 메시지. parentId 가 있으면 같은 대화에 이어서: 직전 메시지의 브랜치·Claude 세션을 이어받는다.
     * 직전 메시지는 대화의 마지막이어야 하고 끝난 상태({@link #FOLLOWABLE})여야 한다.
     */
    public synchronized DevRequest create(String title, String body, Long parentId) {
        String b = body == null ? "" : body.strip();
        if (b.isEmpty()) throw new IllegalArgumentException("요청 내용을 입력해 주세요.");
        if (b.length() > MAX_BODY) throw new IllegalArgumentException("요청 내용이 너무 깁니다.");
        String t = title == null || title.isBlank() ? deriveTitle(b) : title.strip();
        if (t.length() > MAX_TITLE) t = t.substring(0, MAX_TITLE);

        String branch = null;
        String sessionId = null;
        if (parentId != null) {
            DevRequest parent = get(parentId).orElseThrow(() -> new IllegalArgumentException("원 요청이 없습니다."));
            if (!FOLLOWABLE.contains(parent.status())) {
                throw new IllegalStateException("앞 메시지 작업이 끝난 뒤에 이어서 보낼 수 있습니다.");
            }
            Integer children = jdbc.queryForObject("SELECT COUNT(*) FROM dev_request WHERE parent_id = :p",
                    new MapSqlParameterSource("p", parentId), Integer.class);
            if (children != null && children > 0) {
                throw new IllegalStateException("이미 이어진 메시지가 있습니다. 대화의 마지막 메시지에 이어서 보내 주세요.");
            }
            branch = parent.branch();
            sessionId = parent.sessionId();
            if (SUPERSEDE_ON_FOLLOW.contains(parent.status())) {
                jdbc.update("UPDATE dev_request SET status = :s WHERE id = :id",
                        new MapSqlParameterSource("s", SUPERSEDED).addValue("id", parentId));
            }
        }

        var params = new MapSqlParameterSource()
                .addValue("parentId", parentId).addValue("title", t).addValue("body", b)
                .addValue("branch", branch).addValue("sessionId", sessionId);
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO dev_request (parent_id, title, body, status, branch, session_id)
                VALUES (:parentId, :title, :body, 'QUEUED', :branch, :sessionId)""", params, key, new String[]{"id"});
        return get(key.getKey().longValue()).orElseThrow();
    }

    static String deriveTitle(String body) {
        String first = body.lines().filter(l -> !l.isBlank()).findFirst().orElse("개발 요청").strip();
        return first.length() > 60 ? first.substring(0, 60) + "…" : first;
    }

    /** 대기 중인 요청 취소. */
    public DevRequest cancel(long id) {
        return transition(id, Set.of(QUEUED), CANCELLED, null);
    }

    /** dev 에서 확인 끝 → 운영 반영 승인. 워커가 다음 차례에 master 머지·운영 배포. */
    public DevRequest approve(long id) {
        DevRequest cur = get(id).orElseThrow(() -> new IllegalArgumentException("요청이 없습니다."));
        if (!canApprove(cur.status(), cur.commitSha())) {
            throw new IllegalStateException("운영에 반영할 코드 변경이 없습니다.");
        }
        return transition(id, Set.of(DEV_READY, ANSWERED), APPROVED, "approved_at");
    }

    /** dev 반영됨, 또는 답만 했지만 대화 브랜치에 앞서 만든 커밋이 있는 경우. */
    static boolean canApprove(String status, String commitSha) {
        return DEV_READY.equals(status) || (ANSWERED.equals(status) && commitSha != null && !commitSha.isBlank());
    }

    /** 반려. 브랜치는 남겨 둔다(워커가 정리). */
    public DevRequest reject(long id) {
        return transition(id, Set.of(DEV_READY, ANSWERED, FAILED), REJECTED, "finished_at");
    }

    private synchronized DevRequest transition(long id, Set<String> from, String to, String stampCol) {
        DevRequest cur = get(id).orElseThrow(() -> new IllegalArgumentException("요청이 없습니다."));
        if (!from.contains(cur.status())) {
            throw new IllegalStateException("지금 상태(" + cur.status() + ")에서는 할 수 없습니다.");
        }
        String stamp = stampCol == null ? "" : ", " + stampCol + " = NOW()";
        jdbc.update("UPDATE dev_request SET status = :to" + stamp + " WHERE id = :id AND status = :from",
                new MapSqlParameterSource("to", to).addValue("id", id).addValue("from", cur.status()));
        return get(id).orElseThrow();
    }

    // ── 워커 ─────────────────────────────────────────────────────────────

    /**
     * 다음 작업을 하나 가져가며 상태를 바꾼다. 승인된 운영 반영(APPROVED→PROMOTING)이 새 요청(QUEUED→RUNNING)보다 먼저.
     * 이미 진행 중인 작업이 있으면 아무것도 주지 않는다. 오래 소식이 없는 진행 중 작업은 먼저 FAILED 로 정리한다.
     */
    public synchronized Optional<DevRequest> claim() {
        jdbc.update("""
                UPDATE dev_request SET status = 'FAILED', finished_at = NOW(),
                       error = CONCAT_WS('\\n', error, '작업자 응답 없음(%d분) — 서버 워커가 중단됐을 수 있습니다.')
                 WHERE status IN ('RUNNING', 'PROMOTING') AND updated_at < NOW() - INTERVAL %d MINUTE"""
                .formatted(STALE_MINUTES, STALE_MINUTES), new MapSqlParameterSource());

        Integer busy = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dev_request WHERE status IN ('RUNNING', 'PROMOTING')",
                new MapSqlParameterSource(), Integer.class);
        if (busy != null && busy > 0) return Optional.empty();

        for (String[] step : new String[][]{{APPROVED, PROMOTING}, {QUEUED, RUNNING}}) {
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM dev_request WHERE status = :s ORDER BY id LIMIT 1",
                    new MapSqlParameterSource("s", step[0]), Long.class);
            if (ids.isEmpty()) continue;
            String startCol = RUNNING.equals(step[1]) ? ", started_at = NOW(), error = NULL" : ", error = NULL";
            int n = jdbc.update("UPDATE dev_request SET status = :to, updated_at = NOW()" + startCol
                                + " WHERE id = :id AND status = :from",
                    new MapSqlParameterSource("to", step[1]).addValue("id", ids.get(0)).addValue("from", step[0]));
            if (n == 1) return get(ids.get(0));
        }
        return Optional.empty();
    }

    /** 워커의 진행/결과 보고. 진행 중(RUNNING/PROMOTING)인 요청에만 받는다. */
    public synchronized DevRequest report(long id, Report r) {
        DevRequest cur = get(id).orElseThrow(() -> new IllegalArgumentException("요청이 없습니다."));
        if (!RUNNING.equals(cur.status()) && !PROMOTING.equals(cur.status())) {
            throw new IllegalStateException("진행 중인 요청이 아닙니다: " + cur.status());
        }
        String status = r.status() == null ? cur.status() : r.status();
        if (!WORKER_STATUSES.contains(status)) throw new IllegalArgumentException("허용되지 않는 상태: " + status);
        boolean ok = status.equals(cur.status()) || FAILED.equals(status)
                     || (RUNNING.equals(cur.status()) && (DEV_READY.equals(status) || ANSWERED.equals(status)))
                     || (PROMOTING.equals(cur.status()) && DONE.equals(status));
        if (!ok) throw new IllegalStateException(cur.status() + " → " + status + " 로 바꿀 수 없습니다.");

        var p = new MapSqlParameterSource("id", id)
                .addValue("status", status)
                .addValue("branch", r.branch()).addValue("commitSha", r.commitSha())
                .addValue("sessionId", r.sessionId()).addValue("summary", r.summary())
                .addValue("diffStat", r.diffStat()).addValue("logTail", tail(r.logTail()))
                .addValue("error", r.error()).addValue("costUsd", r.costUsd());

        String extra = "";
        if (TERMINAL.contains(status)) extra += ", finished_at = NOW()";
        if (DONE.equals(status)) extra += ", promoted_at = NOW()";
        jdbc.update("""
                UPDATE dev_request SET status = :status, updated_at = NOW(),
                       branch = COALESCE(:branch, branch), commit_sha = COALESCE(:commitSha, commit_sha),
                       session_id = COALESCE(:sessionId, session_id), summary = COALESCE(:summary, summary),
                       diff_stat = COALESCE(:diffStat, diff_stat), log_tail = COALESCE(:logTail, log_tail),
                       error = COALESCE(:error, error), cost_usd = COALESCE(:costUsd, cost_usd)""" + extra + """
                 WHERE id = :id""", p);
        return get(id).orElseThrow();
    }

    /** 브랜치의 가장 최근 요청 상태. 워커가 다 끝난 브랜치의 worktree 를 지울 때 쓴다. */
    public Optional<String> latestStatusOfBranch(String branch) {
        return jdbc.queryForList(
                "SELECT status FROM dev_request WHERE branch = :b ORDER BY id DESC LIMIT 1",
                new MapSqlParameterSource("b", branch), String.class).stream().findFirst();
    }

    static String tail(String s) {
        if (s == null || s.length() <= MAX_LOG) return s;
        return "…\n" + s.substring(s.length() - MAX_LOG);
    }
}

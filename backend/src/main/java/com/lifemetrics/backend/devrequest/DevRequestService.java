package com.lifemetrics.backend.devrequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
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
 * 개발 요청함 — 관리자와 Claude 의 상시 대화.
 * <p>
 * 대화 하나 = 브랜치 devreq/&lt;root_id&gt; 하나 + Claude 세션 하나. 대화가 열려 있는 동안(dev_conversation OPEN/LIVE)
 * 서버 워커(deploy/dev-request-worker.py)가 그 대화 전용 Claude 프로세스를 띄워 두고, 메시지(kind=MESSAGE)를 바로 넣는다.
 * 대화 중에는 코드만 고치고(커밋), [dev 배포]/[대화 종료] 때 DEPLOY 행을 만들어 한 번에 빌드·개발 서버 배포한다.
 * 개발 서버에서 확인한 DEPLOY(DEV_READY)를 승인하면 master 머지·운영 배포.
 * <p>
 * 워커는 {@link #poll} 로 할 일을 받아 간다(새 일이 생기면 바로 응답하는 long-poll). 메시지는 대화마다 동시에,
 * 빌드가 필요한 일(DEPLOY, 운영 반영)은 dev 서버가 하나라서 한 번에 하나만. 상태 흐름은 sql/dev_request.sql 참고.
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
    /** 메시지에 Claude 가 답함(코드를 바꿨으면 commit_sha·diff_stat 이 있음, 배포는 아직). */
    public static final String ANSWERED = "ANSWERED";

    /** 메시지 종류 */
    public static final String MESSAGE = "MESSAGE";
    public static final String DEPLOY = "DEPLOY";

    /** 작업 대상 저장소. 대화의 첫 메시지에서 정하고 이어지는 행이 물려받는다. */
    public static final String REPO_APP = "lifemetrics";
    public static final String REPO_PIPELINE = "data_pipeline";
    static final Set<String> REPOS = Set.of(REPO_APP, REPO_PIPELINE);

    /** 대화 상태: 시작 요청됨(워커가 프로세스 띄우는 중) → 프로세스 대기 중 → 종료 */
    public static final String OPEN = "OPEN";
    public static final String LIVE = "LIVE";
    public static final String CLOSED = "CLOSED";

    /** 워커가 보고할 수 있는 상태. RUNNING/PROMOTING 은 진행 로그만 갱신(heartbeat). */
    static final Set<String> WORKER_STATUSES = Set.of(RUNNING, PROMOTING, DEV_READY, ANSWERED, FAILED, DONE);
    private static final Set<String> TERMINAL = Set.of(DEV_READY, ANSWERED, FAILED, DONE);
    /** 이 상태인 대화의 마지막 메시지에 이어서 말할 수 있다. */
    static final Set<String> FOLLOWABLE = Set.of(DEV_READY, ANSWERED, FAILED, DONE, REJECTED, CANCELLED);
    /** 진행 중(대화가 바쁨) */
    static final Set<String> ACTIVE = Set.of(QUEUED, RUNNING, APPROVED, PROMOTING);

    /** 이 시간 동안 워커 소식이 없으면 멈춘 것으로 보고 FAILED 처리한다. */
    static final int STALE_MINUTES = 30;
    /** 워커 long-poll 최대 대기 */
    static final long MAX_POLL_WAIT_MS = 25_000;

    static final int MAX_TITLE = 200;
    static final int MAX_BODY = 20_000;
    private static final int MAX_LOG = 60_000;

    private final NamedParameterJdbcTemplate jdbc;

    /** 워커를 깨우는 신호. 새 메시지·대화 시작/종료·승인 때 version 을 올린다. */
    private final Object signal = new Object();
    private long version = System.currentTimeMillis();

    public DevRequestService(@Qualifier("ridingDataSource") DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public record DevRequest(Long id, Long parentId, String kind, Long rootId, String repo,
                             String title, String body, String status,
                             String branch, String commitSha, String sessionId,
                             String summary, String diffStat, String logTail, String error, BigDecimal costUsd,
                             String createdAt, String startedAt, String finishedAt,
                             String approvedAt, String promotedAt, String updatedAt) {
    }

    /** 대화 상태. undeployed: 개발 서버에 아직 안 올린 커밋이 있음. */
    public record Conversation(long rootId, String state, String openedAt, String lastActiveAt,
                               String closedAt, String closeReason, boolean undeployed) {
    }

    /** 워커가 띄워 둘 대화(OPEN/LIVE). sessionId 가 있으면 --resume. */
    public record WorkerConversation(long rootId, String state, String repo, String branch, String sessionId) {
    }

    /** 워커 poll 응답. job 이 있으면 방금 가져간 일. */
    public record Poll(long version, DevRequest job, List<WorkerConversation> conversations) {
    }

    /** 워커 보고. null 인 필드는 바꾸지 않는다. */
    public record Report(String status, String branch, String commitSha, String sessionId,
                         String summary, String diffStat, String logTail, String error, BigDecimal costUsd) {
    }

    private static final String COLS = """
            id, parent_id, kind, root_id, repo, title, body, status, branch, commit_sha, session_id, summary, diff_stat,
            %s AS log_tail, error, cost_usd, created_at, started_at, finished_at, approved_at, promoted_at, updated_at""";

    private static final RowMapper<DevRequest> MAPPER = (rs, i) -> new DevRequest(
            rs.getLong("id"), (Long) rs.getObject("parent_id", Long.class),
            rs.getString("kind"), (Long) rs.getObject("root_id", Long.class), rs.getString("repo"),
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

    /** 개발 서버에 아직 안 올린 커밋이 있는지. c.root_id 를 쓰는 SQL 식. */
    private static final String UNDEPLOYED_EXPR = """
            (SELECT COALESCE(MAX(r.id), 0) FROM dev_request r
              WHERE r.root_id = c.root_id AND r.kind = 'MESSAGE' AND r.commit_sha IS NOT NULL)
            > (SELECT COALESCE(MAX(r.id), 0) FROM dev_request r
              WHERE r.root_id = c.root_id
                AND ((r.kind = 'DEPLOY' AND r.status NOT IN ('FAILED', 'CANCELLED', 'REJECTED', 'SUPERSEDED'))
                  OR (r.kind = 'MESSAGE' AND r.status IN ('DEV_READY', 'APPROVED', 'PROMOTING', 'DONE'))))""";

    static String branchOf(long rootId) {
        return "devreq/" + rootId;
    }

    // ── 앱 ───────────────────────────────────────────────────────────────

    /** 목록은 진행 로그를 빼고 돌려준다(크다). */
    public List<DevRequest> list() {
        return jdbc.query("SELECT " + COLS.formatted("NULL") + " FROM dev_request ORDER BY id DESC LIMIT 300",
                MAPPER);
    }

    public Optional<DevRequest> get(long id) {
        return jdbc.query("SELECT " + COLS.formatted("log_tail") + " FROM dev_request WHERE id = :id",
                new MapSqlParameterSource("id", id), MAPPER).stream().findFirst();
    }

    public List<Conversation> conversations() {
        return jdbc.query("SELECT c.*, " + UNDEPLOYED_EXPR + " AS undeployed"
                          + " FROM dev_conversation c ORDER BY c.root_id DESC LIMIT 300",
                new MapSqlParameterSource(), (rs, i) -> conversation(rs));
    }

    public Optional<Conversation> conversation(long rootId) {
        return jdbc.query("SELECT c.*, " + UNDEPLOYED_EXPR + " AS undeployed"
                          + " FROM dev_conversation c WHERE c.root_id = :r",
                new MapSqlParameterSource("r", rootId), (rs, i) -> conversation(rs)).stream().findFirst();
    }

    private static Conversation conversation(ResultSet rs) throws SQLException {
        return new Conversation(rs.getLong("root_id"), rs.getString("state"), ts(rs, "opened_at"),
                ts(rs, "last_active_at"), ts(rs, "closed_at"), rs.getString("close_reason"),
                rs.getBoolean("undeployed"));
    }

    /**
     * 새 메시지. parentId 가 있으면 같은 대화에 이어서(브랜치·Claude 세션을 이어받음). 대화가 닫혀 있으면 다시 연다.
     * 직전 메시지는 대화의 마지막이어야 하고 끝난 상태({@link #FOLLOWABLE})여야 한다.
     */
    public synchronized DevRequest create(String title, String body, Long parentId, String repo) {
        String b = body == null ? "" : body.strip();
        if (b.isEmpty()) throw new IllegalArgumentException("요청 내용을 입력해 주세요.");
        if (b.length() > MAX_BODY) throw new IllegalArgumentException("요청 내용이 너무 깁니다.");
        String t = title == null || title.isBlank() ? deriveTitle(b) : title.strip();
        if (t.length() > MAX_TITLE) t = t.substring(0, MAX_TITLE);

        DevRequest created;
        if (parentId == null) {
            created = insert(MESSAGE, null, null, t, b, null, normalizeRepo(repo));
        } else {
            // 이어지는 메시지는 대화의 저장소를 그대로 쓴다(repo 인자는 무시)
            DevRequest parent = get(parentId).orElseThrow(() -> new IllegalArgumentException("원 요청이 없습니다."));
            long root = rootOf(parent);
            checkFollow(root, parent);
            created = insert(MESSAGE, parentId, root, t, b, parent.sessionId(), parent.repo());
        }
        openConversation(created.rootId());
        bump();
        return created;
    }

    /** [대화 시작] — 워커가 Claude 프로세스를 미리 띄워 둔다(첫 메시지가 빨라짐). */
    public synchronized Conversation start(long rootId) {
        lastOf(rootId).orElseThrow(() -> new IllegalArgumentException("대화가 없습니다."));
        openConversation(rootId);
        bump();
        return conversation(rootId).orElseThrow();
    }

    /** [dev 배포] — 대화 브랜치를 빌드해 개발 서버에 올리는 DEPLOY 행을 대화 끝에 붙인다. */
    public synchronized DevRequest deploy(long rootId) {
        DevRequest last = lastOf(rootId).orElseThrow(() -> new IllegalArgumentException("대화가 없습니다."));
        checkFollow(rootId, last);
        if (!undeployed(rootId)) throw new IllegalStateException("개발 서버에 올릴 새 변경이 없습니다.");
        DevRequest d = insert(DEPLOY, last.id(), rootId, "개발 서버 배포", "개발 서버 배포", last.sessionId(), last.repo());
        touch(rootId);
        bump();
        return d;
    }

    /**
     * [대화 종료] — Claude 프로세스를 닫고, 개발 서버에 안 올린 변경이 있으면 배포까지 건다.
     * Claude 가 답하는 중에는 닫을 수 없다(작업이 중간에 끊기지 않게).
     */
    public synchronized Conversation close(long rootId) {
        DevRequest last = lastOf(rootId).orElseThrow(() -> new IllegalArgumentException("대화가 없습니다."));
        if (MESSAGE.equals(last.kind()) && (QUEUED.equals(last.status()) || RUNNING.equals(last.status()))) {
            throw new IllegalStateException("Claude 가 답하는 중이에요. 끝난 뒤에 종료해 주세요.");
        }
        closeConversation(rootId, "대화 종료");
        if (FOLLOWABLE.contains(last.status()) && !busyPipeline(rootId) && undeployed(rootId)) {
            insert(DEPLOY, last.id(), rootId, "개발 서버 배포", "대화 종료 — 개발 서버 배포", last.sessionId(), last.repo());
        }
        bump();
        return conversation(rootId).orElseThrow();
    }

    static String normalizeRepo(String repo) {
        if (repo == null || repo.isBlank()) return REPO_APP;
        String r = repo.strip();
        if (!REPOS.contains(r)) throw new IllegalArgumentException("알 수 없는 저장소: " + r);
        return r;
    }

    static String deriveTitle(String body) {
        String first = body.lines().filter(l -> !l.isBlank()).findFirst().orElse("개발 요청").strip();
        return first.length() > 60 ? first.substring(0, 60) + "…" : first;
    }

    /** 대기 중인 요청 취소. */
    public DevRequest cancel(long id) {
        DevRequest r = transition(id, Set.of(QUEUED), CANCELLED, null);
        bump();
        return r;
    }

    /** 개발 서버에서 확인 끝 → 운영 반영 승인. 워커가 다음 차례에 master 머지·운영 배포. */
    public synchronized DevRequest approve(long id) {
        DevRequest cur = get(id).orElseThrow(() -> new IllegalArgumentException("요청이 없습니다."));
        if (!canApprove(cur.status())) {
            throw new IllegalStateException("개발 서버에 배포된 변경만 운영에 반영할 수 있습니다.");
        }
        long root = rootOf(cur);
        DevRequest last = lastOf(root).orElseThrow();
        if (ACTIVE.contains(last.status())) {
            throw new IllegalStateException("대화에서 진행 중인 작업이 끝난 뒤에 반영할 수 있습니다.");
        }
        DevRequest r = transition(id, Set.of(DEV_READY), APPROVED, "approved_at");
        bump();
        return r;
    }

    /** 개발 서버에 올라간 상태(DEV_READY)만 운영 반영할 수 있다. 그 뒤 새 커밋이 생기면 SUPERSEDED 로 바뀐다. */
    static boolean canApprove(String status) {
        return DEV_READY.equals(status);
    }

    /** 반려. 대화도 닫는다. 브랜치는 남겨 둔다(워커가 정리). */
    public synchronized DevRequest reject(long id) {
        DevRequest r = transition(id, Set.of(DEV_READY, ANSWERED, FAILED), REJECTED, "finished_at");
        closeConversation(rootOf(r), "반려");
        bump();
        return r;
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

    // ── 대화 내부 ────────────────────────────────────────────────────────

    /** 이어 붙이기 검사: parent 가 대화의 마지막이고 끝난 상태, 운영 반영 중이 아님. 실패 메시지는 SUPERSEDED 로. */
    private void checkFollow(long root, DevRequest parent) {
        if (!FOLLOWABLE.contains(parent.status())) {
            throw new IllegalStateException("앞 작업이 끝난 뒤에 이어서 보낼 수 있습니다.");
        }
        DevRequest last = lastOf(root).orElseThrow();
        if (!last.id().equals(parent.id())) {
            throw new IllegalStateException("이미 이어진 메시지가 있습니다. 대화의 마지막 메시지에 이어서 보내 주세요.");
        }
        if (busyPipeline(root)) {
            throw new IllegalStateException("운영 반영이 끝난 뒤에 이어서 보낼 수 있습니다.");
        }
        if (FAILED.equals(parent.status())) {
            jdbc.update("UPDATE dev_request SET status = :s WHERE id = :id",
                    new MapSqlParameterSource("s", SUPERSEDED).addValue("id", parent.id()));
        }
    }

    /** 운영 반영 대기/중인 행이 있으면 대화 브랜치를 건드리면 안 된다. */
    private boolean busyPipeline(long root) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dev_request WHERE root_id = :r AND status IN ('APPROVED', 'PROMOTING')",
                new MapSqlParameterSource("r", root), Integer.class);
        return n != null && n > 0;
    }

    private DevRequest insert(String kind, Long parentId, Long root, String title, String body, String sessionId,
                              String repo) {
        var params = new MapSqlParameterSource()
                .addValue("parentId", parentId).addValue("kind", kind).addValue("root", root)
                .addValue("repo", repo == null ? REPO_APP : repo)
                .addValue("title", title).addValue("body", body)
                .addValue("branch", root == null ? null : branchOf(root)).addValue("sessionId", sessionId);
        var key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO dev_request (parent_id, kind, root_id, repo, title, body, status, branch, session_id)
                VALUES (:parentId, :kind, :root, :repo, :title, :body, 'QUEUED', :branch, :sessionId)""",
                params, key, new String[]{"id"});
        long id = key.getKey().longValue();
        if (root == null) {
            jdbc.update("UPDATE dev_request SET root_id = :id, branch = :b WHERE id = :id",
                    new MapSqlParameterSource("id", id).addValue("b", branchOf(id)));
        }
        return get(id).orElseThrow();
    }

    /** 대화의 첫 메시지 id. root_id 가 없는 예전 행은 parent 사슬을 따라간다. */
    long rootOf(DevRequest r) {
        if (r.rootId() != null) return r.rootId();
        DevRequest cur = r;
        for (int i = 0; i < 200 && cur.parentId() != null; i++) {
            Optional<DevRequest> p = get(cur.parentId());
            if (p.isEmpty()) break;
            cur = p.get();
            if (cur.rootId() != null) return cur.rootId();
        }
        return cur.id();
    }

    private Optional<DevRequest> lastOf(long root) {
        return jdbc.query("SELECT " + COLS.formatted("NULL")
                          + " FROM dev_request WHERE root_id = :r OR id = :r ORDER BY id DESC LIMIT 1",
                new MapSqlParameterSource("r", root), MAPPER).stream().findFirst();
    }

    private boolean undeployed(long root) {
        Boolean b = jdbc.queryForObject("SELECT " + UNDEPLOYED_EXPR + " FROM (SELECT :r AS root_id) c",
                new MapSqlParameterSource("r", root), Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    /** 대화를 연다(닫혀 있었으면 다시 OPEN, 열려 있으면 활동 시각만 갱신). */
    private void openConversation(long root) {
        // ON DUPLICATE KEY UPDATE 는 왼쪽부터 적용되므로 state 를 마지막에 바꾼다
        jdbc.update("""
                INSERT INTO dev_conversation (root_id, state, opened_at, last_active_at) VALUES (:r, 'OPEN', NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    opened_at = IF(state = 'CLOSED', NOW(), opened_at),
                    closed_at = IF(state = 'CLOSED', NULL, closed_at),
                    close_reason = IF(state = 'CLOSED', NULL, close_reason),
                    last_active_at = NOW(),
                    state = IF(state = 'CLOSED', 'OPEN', state)""", new MapSqlParameterSource("r", root));
    }

    private void touch(long root) {
        jdbc.update("UPDATE dev_conversation SET last_active_at = NOW() WHERE root_id = :r",
                new MapSqlParameterSource("r", root));
    }

    private void closeConversation(long root, String reason) {
        jdbc.update("""
                UPDATE dev_conversation SET state = 'CLOSED', closed_at = NOW(), close_reason = :reason
                 WHERE root_id = :r AND state <> 'CLOSED'""",
                new MapSqlParameterSource("r", root).addValue("reason", reason));
    }

    private void bump() {
        synchronized (signal) {
            version++;
            signal.notifyAll();
        }
    }

    private long currentVersion() {
        synchronized (signal) {
            return version;
        }
    }

    // ── 워커 ─────────────────────────────────────────────────────────────

    /**
     * 워커 long-poll. 가져갈 일이 있거나 대화 목록이 바뀌었으면(known 과 version 이 다르면) 바로, 아니면
     * 최대 waitMs 동안 기다렸다가 돌려준다.
     */
    public Poll poll(long known, long waitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0, Math.min(waitMs, MAX_POLL_WAIT_MS));
        while (true) {
            Optional<DevRequest> job = claim();
            long v = currentVersion();
            long left = deadline - System.currentTimeMillis();
            if (job.isPresent() || v != known || left <= 0) {
                return new Poll(v, job.orElse(null), workerConversations());
            }
            synchronized (signal) {
                if (version == known) signal.wait(Math.min(left, 5_000)); // 5초마다 claim 다시(멈춘 작업 정리 등)
            }
        }
    }

    /**
     * 다음 일을 하나 가져가며 상태를 바꾼다. 오래 소식이 없는 진행 중 작업은 먼저 FAILED 로 정리한다.
     * <ul>
     *   <li>빌드가 필요한 일(운영 반영 APPROVED→PROMOTING, DEPLOY QUEUED→RUNNING)은 한 번에 하나, 운영 반영 먼저.</li>
     *   <li>메시지(MESSAGE QUEUED→RUNNING)는 대화마다 동시에(한 대화 안에서는 사슬 규칙상 하나뿐).</li>
     * </ul>
     */
    public synchronized Optional<DevRequest> claim() {
        jdbc.update("""
                UPDATE dev_request SET status = 'FAILED', finished_at = NOW(),
                       error = CONCAT_WS('\\n', error, '작업자 응답 없음(%d분) — 서버 워커가 중단됐을 수 있습니다.')
                 WHERE status IN ('RUNNING', 'PROMOTING') AND updated_at < NOW() - INTERVAL %d MINUTE"""
                .formatted(STALE_MINUTES, STALE_MINUTES), new MapSqlParameterSource());

        Integer busy = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dev_request
                 WHERE status = 'PROMOTING' OR (status = 'RUNNING' AND kind = 'DEPLOY')""",
                new MapSqlParameterSource(), Integer.class);
        boolean pipelineFree = busy == null || busy == 0;

        String[][] steps = {
                {APPROVED, PROMOTING, "1=1"},
                {QUEUED, RUNNING, "kind = 'DEPLOY'"},
                {QUEUED, RUNNING, "kind = 'MESSAGE'"},
        };
        for (String[] step : steps) {
            if (!pipelineFree && !step[2].contains("MESSAGE")) continue;
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM dev_request WHERE status = :s AND " + step[2] + " ORDER BY id LIMIT 1",
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

    /** 워커가 띄워 둬야 할 대화. 세션은 그 대화에서 가장 최근에 받은 Claude 세션. */
    public List<WorkerConversation> workerConversations() {
        return jdbc.query("""
                SELECT c.root_id, c.state,
                       COALESCE((SELECT r.repo FROM dev_request r WHERE r.id = c.root_id), 'lifemetrics') AS repo,
                       (SELECT r.session_id FROM dev_request r
                         WHERE (r.root_id = c.root_id OR r.id = c.root_id) AND r.session_id IS NOT NULL
                         ORDER BY r.id DESC LIMIT 1) AS session_id
                  FROM dev_conversation c WHERE c.state IN ('OPEN', 'LIVE') ORDER BY c.root_id""",
                new MapSqlParameterSource(), (rs, i) -> new WorkerConversation(
                        rs.getLong("root_id"), rs.getString("state"), rs.getString("repo"), branchOf(rs.getLong("root_id")),
                        rs.getString("session_id")));
    }

    /** 워커가 대화 프로세스를 띄웠거나(LIVE) 닫았음(CLOSED: 입력 없음 자동 종료, 프로세스 종료 등)을 알린다. */
    public void workerConversationState(long root, String state, String reason) {
        if (LIVE.equals(state)) {
            jdbc.update("UPDATE dev_conversation SET state = 'LIVE' WHERE root_id = :r AND state = 'OPEN'",
                    new MapSqlParameterSource("r", root));
        } else if (CLOSED.equals(state)) {
            closeConversation(root, reason == null || reason.isBlank() ? "워커 종료" : reason);
        } else {
            throw new IllegalArgumentException("허용되지 않는 상태: " + state);
        }
        bump(); // 기다리던 poll 이 새 대화 목록으로 응답하게
    }

    /** 워커가 (다시) 시작함 — 하던 일은 끊겼으니 FAILED, 떠 있던 대화는 OPEN 으로(다시 띄우게). */
    public synchronized void workerStarted() {
        jdbc.update("""
                UPDATE dev_request SET status = 'FAILED', finished_at = NOW(),
                       error = CONCAT_WS('\\n', error, '서버 워커가 다시 시작돼 작업이 중단됐습니다. 다시 보내 주세요.')
                 WHERE status IN ('RUNNING', 'PROMOTING')""", new MapSqlParameterSource());
        jdbc.update("UPDATE dev_conversation SET state = 'OPEN' WHERE state = 'LIVE'", new MapSqlParameterSource());
        bump();
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

        long root = rootOf(cur);
        // 메시지에서 새 커밋이 생기면 앞서 개발 서버에 올린 배포는 낡은 것 — 운영 반영 버튼을 내린다
        if (MESSAGE.equals(cur.kind()) && ANSWERED.equals(status) && r.commitSha() != null) {
            jdbc.update("UPDATE dev_request SET status = 'SUPERSEDED' WHERE root_id = :r AND status = 'DEV_READY'",
                    new MapSqlParameterSource("r", root));
        }
        if (TERMINAL.contains(status)) touch(root);
        return get(id).orElseThrow();
    }

    /** 브랜치의 가장 최근 요청 상태와 대화 상태. 워커가 다 끝난 브랜치의 worktree 를 지울 때 쓴다. */
    public Optional<String> latestStatusOfBranch(String branch) {
        return jdbc.queryForList(
                "SELECT status FROM dev_request WHERE branch = :b ORDER BY id DESC LIMIT 1",
                new MapSqlParameterSource("b", branch), String.class).stream().findFirst();
    }

    public Optional<String> conversationStateOfBranch(String branch) {
        if (branch == null || !branch.matches("devreq/\\d+")) return Optional.empty();
        long root = Long.parseLong(branch.substring("devreq/".length()));
        return jdbc.queryForList("SELECT state FROM dev_conversation WHERE root_id = :r",
                new MapSqlParameterSource("r", root), String.class).stream().findFirst();
    }

    static String tail(String s) {
        if (s == null || s.length() <= MAX_LOG) return s;
        return "…\n" + s.substring(s.length() - MAX_LOG);
    }
}

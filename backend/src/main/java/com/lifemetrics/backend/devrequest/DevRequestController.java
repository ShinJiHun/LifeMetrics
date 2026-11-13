package com.lifemetrics.backend.devrequest;

import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * 개발 요청함 (관리자 전용).
 * <ul>
 *   <li>앱: 요청 목록/상세/작성/취소/승인/반려 — 관리자 쿠키. 읽기도 관리자만(요청·로그에 내부 정보가 있음).</li>
 *   <li>서버 워커: /worker/** — {@code Authorization: Bearer <DEV_WORKER_TOKEN>}.
 *       그래서 /worker/** 는 AdminWriteFilter 의 공개 쓰기 경로이고 토큰 검사는 여기서 한다.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dev-requests")
@RequiredArgsConstructor
public class DevRequestController {

    private final DevRequestService service;
    private final AdminWriteFilter adminWriteFilter;

    @Value("${DEV_WORKER_TOKEN:}")
    private String workerToken;

    public record CreateBody(String title, String body, Long parentId) {
    }

    @GetMapping
    public List<DevRequestService.DevRequest> list(HttpServletRequest request) {
        requireAdmin(request);
        return service.list();
    }

    @GetMapping("/{id}")
    public DevRequestService.DevRequest get(HttpServletRequest request, @PathVariable long id) {
        requireAdmin(request);
        return service.get(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public DevRequestService.DevRequest create(HttpServletRequest request, @RequestBody CreateBody body) {
        requireAdmin(request);
        return guard(() -> service.create(body.title(), body.body(), body.parentId()));
    }

    // ── 대화(상시 Claude 프로세스) ─────────────────────────────────────────

    @GetMapping("/conversations")
    public List<DevRequestService.Conversation> conversations(HttpServletRequest request) {
        requireAdmin(request);
        return service.conversations();
    }

    /** [대화 시작] — 워커가 그 대화의 Claude 프로세스를 띄운다. */
    @PostMapping("/conversations/{rootId}/start")
    public DevRequestService.Conversation start(HttpServletRequest request, @PathVariable long rootId) {
        requireAdmin(request);
        return guard(() -> service.start(rootId));
    }

    /** [대화 종료] — 프로세스를 닫고, 안 올린 변경이 있으면 개발 서버 배포. */
    @PostMapping("/conversations/{rootId}/close")
    public DevRequestService.Conversation close(HttpServletRequest request, @PathVariable long rootId) {
        requireAdmin(request);
        return guard(() -> service.close(rootId));
    }

    /** [dev 배포] — 대화 브랜치를 빌드해 개발 서버에 올린다(대화는 계속). */
    @PostMapping("/conversations/{rootId}/deploy")
    public DevRequestService.DevRequest deploy(HttpServletRequest request, @PathVariable long rootId) {
        requireAdmin(request);
        return guard(() -> service.deploy(rootId));
    }

    @PostMapping("/{id}/cancel")
    public DevRequestService.DevRequest cancel(HttpServletRequest request, @PathVariable long id) {
        requireAdmin(request);
        return guard(() -> service.cancel(id));
    }

    @PostMapping("/{id}/approve")
    public DevRequestService.DevRequest approve(HttpServletRequest request, @PathVariable long id) {
        requireAdmin(request);
        return guard(() -> service.approve(id));
    }

    @PostMapping("/{id}/reject")
    public DevRequestService.DevRequest reject(HttpServletRequest request, @PathVariable long id) {
        requireAdmin(request);
        return guard(() -> service.reject(id));
    }

    // ── 서버 워커 ────────────────────────────────────────────────────────

    public record PollBody(Long version, Long waitMs) {
    }

    public record ConversationStateBody(String state, String reason) {
    }

    /** 할 일 + 띄워 둘 대화 목록. 새 일이 없으면 waitMs(최대 25초) 동안 기다렸다 응답한다(long-poll). */
    @PostMapping("/worker/poll")
    public DevRequestService.Poll poll(HttpServletRequest request, @RequestBody(required = false) PollBody body)
            throws InterruptedException {
        requireWorker(request);
        long known = body == null || body.version() == null ? -1 : body.version();
        long wait = body == null || body.waitMs() == null ? 0 : body.waitMs();
        return service.poll(known, wait);
    }

    /** 워커 (재)시작 — 끊긴 작업 정리. */
    @PostMapping("/worker/startup")
    public Map<String, String> startup(HttpServletRequest request) {
        requireWorker(request);
        service.workerStarted();
        return Map.of("status", "ok");
    }

    @PostMapping("/worker/conversations/{rootId}/state")
    public Map<String, String> conversationState(HttpServletRequest request, @PathVariable long rootId,
                                                 @RequestBody ConversationStateBody body) {
        requireWorker(request);
        guard(() -> {
            service.workerConversationState(rootId, body.state(), body.reason());
            return null;
        });
        return Map.of("status", "ok");
    }

    /** 다음 작업. 없으면 204. (예전 워커 호환) */
    @PostMapping("/worker/claim")
    public ResponseEntity<DevRequestService.DevRequest> claim(HttpServletRequest request) {
        requireWorker(request);
        return service.claim().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/worker/{id}/report")
    public DevRequestService.DevRequest report(HttpServletRequest request, @PathVariable long id,
                                               @RequestBody DevRequestService.Report report) {
        requireWorker(request);
        return guard(() -> service.report(id, report));
    }

    @PostMapping("/worker/branch-status")
    public Map<String, String> branchStatus(HttpServletRequest request, @RequestBody Map<String, String> body) {
        requireWorker(request);
        String branch = body.getOrDefault("branch", "");
        return Map.of("status", service.latestStatusOfBranch(branch).orElse(""),
                "conversation", service.conversationStateOfBranch(branch).orElse(""));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }

    private void requireWorker(HttpServletRequest request) {
        if (workerToken == null || workerToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DEV_WORKER_TOKEN 미설정");
        }
        String header = request.getHeader("Authorization");
        boolean ok = header != null && header.startsWith("Bearer ") && MessageDigest.isEqual(
                workerToken.getBytes(StandardCharsets.UTF_8),
                header.substring("Bearer ".length()).trim().getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "워커 토큰이 맞지 않습니다.");
    }

    /** 기본 오류 응답은 사유를 숨기므로(server.error.include-message=never) 이 컨트롤러는 message 를 직접 내려준다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> onError(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }

    private static <T> T guard(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}

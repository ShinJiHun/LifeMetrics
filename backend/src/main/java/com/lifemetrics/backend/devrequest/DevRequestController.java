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

    /** 다음 작업. 없으면 204. */
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
        return Map.of("status", service.latestStatusOfBranch(body.getOrDefault("branch", "")).orElse(""));
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

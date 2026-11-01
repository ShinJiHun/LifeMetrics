package com.lifemetrics.backend.health.controller;

import com.lifemetrics.backend.health.dto.HealthSyncRequest;
import com.lifemetrics.backend.health.service.HealthSyncService;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 폰 앱(Health Connect)의 하루 1번 동기화를 받는다.
 * <p>
 * 앱은 관리자 쿠키 대신 {@code Authorization: Bearer <HEALTH_SYNC_TOKEN>} 으로 인증한다.
 * 그래서 이 경로는 AdminWriteFilter 의 공개 쓰기 경로에 들어 있고, 토큰 검사는 여기서 한다.
 * 토큰이 설정되지 않은 서버에서는 동기화를 받지 않는다.
 */
@RestController
@RequestMapping("/api/health-sync")
@RequiredArgsConstructor
public class HealthSyncController {

    private final HealthSyncService service;
    private final AdminWriteFilter adminWriteFilter;

    @Value("${HEALTH_SYNC_TOKEN:}")
    private String syncToken;

    @Value("${HEALTH_SYNC_USER_ID:1}")
    private long userId;

    @PostMapping
    public ResponseEntity<?> sync(HttpServletRequest request, @RequestBody HealthSyncRequest body) {
        if (syncToken == null || syncToken.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "message", "서버에 HEALTH_SYNC_TOKEN 이 설정되지 않아 동기화를 받을 수 없습니다."));
        }
        if (!hasValidToken(request)) {
            return ResponseEntity.status(401).body(Map.of("message", "동기화 토큰이 없거나 맞지 않습니다."));
        }
        try {
            HealthSyncService.Result r = service.sync(userId, body);
            return ResponseEntity.ok(Map.of(
                    "days", r.days(),
                    "nutrition", r.nutrition(),
                    "exercises", r.exercises(),
                    "deleted", r.deleted(),
                    "summaryDates", r.summaryDates()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 마지막 동기화 정보. 앱(토큰) 또는 관리자만 볼 수 있다. */
    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest request) {
        if (!hasValidToken(request) && !adminWriteFilter.isAdmin(request)) {
            return ResponseEntity.status(401).body(Map.of("message", "동기화 토큰 또는 관리자 로그인이 필요합니다."));
        }
        Map<String, Object> last = service.lastSync(userId);
        Map<String, Object> body = new HashMap<>();
        body.put("lastSync", last);
        return ResponseEntity.ok(body);
    }

    private boolean hasValidToken(HttpServletRequest request) {
        if (syncToken == null || syncToken.isBlank()) return false;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        return MessageDigest.isEqual(
                syncToken.getBytes(StandardCharsets.UTF_8),
                header.substring("Bearer ".length()).trim().getBytes(StandardCharsets.UTF_8));
    }
}

package com.lifemetrics.backend.api;

import com.lifemetrics.backend.security.AdminWriteFilter;
import com.lifemetrics.backend.service.DailyAnalysisService;
import com.lifemetrics.backend.service.DailyInsightService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 종합 분석(앱 '종합 분석' 화면의 하루 분석 글).
 * <p>
 * 일기·업무 일지가 들어 있어 조회도 막는다. 웹은 관리자 쿠키, 폰 앱은 {@code Authorization: Bearer <HEALTH_SYNC_TOKEN>}.
 * AdminWriteFilter 는 이 경로를 쿠키로 막지 않고 여기에 맡긴다.
 * <ul>
 *   <li>GET  /api/ai/insight/daily?date=yyyy-MM-dd → {date, insight, stale} (date 생략 시 어제, 없으면 insight=null)</li>
 *   <li>POST /api/ai/insight/daily/{date} → {date, insight} (그날 기록이 없으면 insight=null)</li>
 * </ul>
 * insight 는 {date, analyzedAt, inputHash, sources, input, result}, result 는
 * {title, summary, athlete, human, work, connections[], goodPoints[], tomorrow[]}.
 * stale 이 true 면 분석 뒤에 기록이 바뀐 것이다(다시 분석하면 반영된다).
 */
@RestController
@RequestMapping("/api/ai/insight/daily")
@RequiredArgsConstructor
public class DailyInsightController {

    private static final long USER_ID = 1L;

    private final DailyInsightService service;
    private final AdminWriteFilter adminWriteFilter;

    @Value("${HEALTH_SYNC_TOKEN:}")
    private String appToken;

    @GetMapping
    public ResponseEntity<?> get(HttpServletRequest request,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!authorized(request)) return forbidden();
        LocalDate target = date != null ? date : LocalDate.now(DailyAnalysisService.KST).minusDays(1);
        Map<String, Object> body = new HashMap<>();
        body.put("date", target.toString());
        service.find(USER_ID, target).ifPresentOrElse(s -> {
            body.put("insight", s.insight());
            body.put("stale", s.stale());
        }, () -> {
            body.put("insight", null);
            body.put("stale", false);
        });
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{date}")
    public ResponseEntity<?> analyze(HttpServletRequest request,
                                     @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!authorized(request)) return forbidden();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("date", date.toString());
            body.put("insight", service.analyzeNow(USER_ID, date).orElse(null));
            return ResponseEntity.ok(body);
        } catch (DailyAnalysisService.AnalysisFailedException e) {
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        }
    }

    private boolean authorized(HttpServletRequest request) {
        return adminWriteFilter.isAdmin(request) || hasValidAppToken(request);
    }

    private boolean hasValidAppToken(HttpServletRequest request) {
        if (appToken == null || appToken.isBlank()) return false;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        return MessageDigest.isEqual(appToken.getBytes(StandardCharsets.UTF_8),
                header.substring("Bearer ".length()).trim().getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다.", "code", "ADMIN_REQUIRED"));
    }
}

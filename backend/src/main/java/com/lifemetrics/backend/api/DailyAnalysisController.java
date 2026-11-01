package com.lifemetrics.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.security.AdminWriteFilter;
import com.lifemetrics.backend.service.DailyAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 하루치 AI 분석. 식단·체성분이 들어 있어 조회도 관리자만 가능하다.
 * (쓰기는 AdminWriteFilter 가 막지만 GET 은 공개라서 여기서 직접 검사한다)
 */
@RestController
@RequestMapping("/api/ai/analysis/daily")
@RequiredArgsConstructor
public class DailyAnalysisController {

    private static final long USER_ID = 1L;

    private final DailyAnalysisService service;
    private final AdminWriteFilter adminWriteFilter;

    /** date 를 생략하면 어제(KST). 분석이 아직 없으면 analysis 가 null 이다. */
    @GetMapping
    public ResponseEntity<?> get(HttpServletRequest request,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!adminWriteFilter.isAdmin(request)) return forbidden();
        LocalDate target = date != null ? date : LocalDate.now(DailyAnalysisService.KST).minusDays(1);
        return ResponseEntity.ok(body(target, service.find(USER_ID, target)));
    }

    /** 지금 다시 분석한다. 그날 기록이 없으면 analysis 가 null 이다. */
    @PostMapping("/{date}")
    public ResponseEntity<?> analyze(HttpServletRequest request,
                                     @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!adminWriteFilter.isAdmin(request)) return forbidden();
        try {
            return ResponseEntity.ok(body(date, service.analyzeNow(USER_ID, date)));
        } catch (DailyAnalysisService.AnalysisFailedException e) {
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        }
    }

    private static Map<String, Object> body(LocalDate date, Optional<JsonNode> analysis) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date.toString());
        body.put("analysis", analysis.orElse(null));
        return body;
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다.", "code", "ADMIN_REQUIRED"));
    }
}

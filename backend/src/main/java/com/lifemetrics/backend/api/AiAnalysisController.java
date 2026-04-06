package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.ActivityAnalysisResponse;
import com.lifemetrics.backend.dto.BrevetAnalysisRequest;
import com.lifemetrics.backend.dto.BrevetAnalysisResponse;
import com.lifemetrics.backend.dto.ExerciseAnalysisResponse;
import com.lifemetrics.backend.service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/analysis")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService analysisService;

    // ================================================================
    // 라이딩 분석 (기존)
    // ================================================================
    @PostMapping("/activity/batch")
    public Map<String, Object> analyzeAllActivities(
            @RequestParam(defaultValue = "1") Long userId) {
        int count = analysisService.analyzeAllActivities(userId);
        return Map.of(
                "success", true,
                "analyzedCount", count,
                "message", count + "개 활동 분석 완료"
        );
    }

    @GetMapping("/activity/{activityId}")
    public ActivityAnalysisResponse getAnalysis(
            @RequestParam(defaultValue = "1") Long userId,
            @PathVariable Long activityId) {
        return analysisService.getAnalysis(userId, activityId);
    }

    // ================================================================
    // 운동 세션 분석
    // ================================================================

    /**
     * 단일 운동 세션 분석
     * POST /api/ai/analysis/exercise/{sessionId}?userId=1
     */
    @PostMapping("/exercise/{sessionId}")
    public ExerciseAnalysisResponse analyzeExerciseSession(
            @RequestParam(defaultValue = "1") Long userId,
            @PathVariable Long sessionId) {
        return analysisService.analyzeExerciseSession(userId, sessionId);
    }

    /**
     * 운동 세션 분석 결과 조회 (DB에서)
     * GET /api/ai/analysis/exercise/{sessionId}?userId=1
     */
    @GetMapping("/exercise/{sessionId}")
    public ExerciseAnalysisResponse getExerciseAnalysis(
            @RequestParam(defaultValue = "1") Long userId,
            @PathVariable Long sessionId) {
        return analysisService.getExerciseAnalysis(userId, sessionId);
    }

    /**
     * 모든 운동 세션 일괄 분석 (배치)
     * POST /api/ai/analysis/exercise/batch?userId=1
     */
    @PostMapping("/exercise/batch")
    public Map<String, Object> analyzeAllExerciseSessions(
            @RequestParam(defaultValue = "1") Long userId) {
        int count = analysisService.analyzeAllExerciseSessions(userId);
        return Map.of(
                "success", true,
                "analyzedCount", count,
                "message", count + "개 운동 세션 분석 완료"
        );
    }

    /**
     * 월간 운동 패턴 분석
     * POST /api/ai/analysis/exercise/monthly?userId=1&month=2025-12
     */
    @PostMapping("/exercise/monthly")
    public ExerciseAnalysisResponse analyzeMonthlyExercise(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam String month) {
        return analysisService.analyzeMonthlyExercise(userId, month);
    }

    @PostMapping("/brevet")
    public BrevetAnalysisResponse analyzeBrevetPlan(
            @RequestBody BrevetAnalysisRequest request) {
        return analysisService.analyzeBrevetPlan(request);
    }

    @PostMapping("/activity/{activityId}")
    public ResponseEntity<?> reAnalyzeActivity(
            @PathVariable Long activityId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(analysisService.reAnalyzeActivity(userId, activityId));
    }

}

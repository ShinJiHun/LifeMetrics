package com.lifemetrics.backend.scheduler;

import com.lifemetrics.backend.service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 저장 직후 비동기 분석이 실패했거나 빠진 운동 세션을 매일 밤 채운다.
// dev 서버는 DB 를 공유하므로 app.scheduling.enabled=false 로 꺼서 중복 호출을 막는다(기본 true).
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class ExerciseAnalysisScheduler {

    private final AiAnalysisService analysisService;

    @Scheduled(cron = "0 59 23 * * *")
    public void analyzeMissingExerciseSessions() {
        int count = analysisService.analyzeAllExerciseSessions(1L);
        System.out.println("🏋️ 밤 운동 분석 배치: " + count + "개 세션 분석");
    }
}

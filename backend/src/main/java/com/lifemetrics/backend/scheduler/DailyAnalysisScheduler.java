package com.lifemetrics.backend.scheduler;

import com.lifemetrics.backend.service.DailyAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 매일 01:30(KST)에 전날 기록을 AI 로 분석한다. 00:00~00:10 의 Garmin 다운로드·FIT 파싱이 끝난 뒤에 돈다.
// dev 서버는 DB 를 공유하므로 app.scheduling.enabled=false 로 꺼서 중복 호출을 막는다(기본 true).
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class DailyAnalysisScheduler {

    private final DailyAnalysisService service;

    @Scheduled(cron = "0 30 1 * * *", zone = "Asia/Seoul")
    public void analyzeYesterday() {
        int count = service.runNightly(1L, LocalDate.now(DailyAnalysisService.KST));
        System.out.println("📊 일일 분석 배치: " + count + "일 분석");
    }
}

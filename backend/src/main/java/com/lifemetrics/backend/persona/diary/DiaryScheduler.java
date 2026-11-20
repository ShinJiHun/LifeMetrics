package com.lifemetrics.backend.persona.diary;

import com.lifemetrics.backend.service.DailyAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 매일 22:00 그날 일기 초안을 만든다. 게시는 하지 않는다 — 자기 전에 화면에서 확인하고 게시.
 * 22시 이후에 올린 메모는 화면의 [다시 만들기]로 반영한다. 고친 초안·게시한 일기는 건드리지 않는다.
 * dev 서버는 app.scheduling.enabled=false 로 꺼진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DiaryScheduler {

    private final DiaryService service;

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void nightlyDraft() {
        LocalDate today = LocalDate.now(DailyAnalysisService.KST);
        try {
            service.generate(today, false).ifPresentOrElse(
                    d -> log.info("일기 초안 생성 {}", today),
                    () -> log.info("일기 초안: {} 메모 없음 또는 변경 없음, 건너뜀", today));
        } catch (Exception e) {
            log.warn("일기 초안 배치 실패 {}: {}", today, e.getMessage());
        }
    }
}

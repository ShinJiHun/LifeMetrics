package com.lifemetrics.backend.persona.diary;

import com.lifemetrics.backend.service.DailyAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 매일 새벽 01:10 전날 일기 초안을 만든다(01:00 PT 시트 동기화 다음). 게시는 하지 않는다 — 아침에 화면에서 확인하고 게시.
 * 새벽에 돌려야 늦게 끝난 운동, 시트에 적은 PT 기록, 자정에 동기화되는 걸음 수, 밤에 쓴 업무 일지·메모까지 하루치가 다 모인다.
 * 그날 바로 보고 싶으면 화면의 [지금 만들기]. 고친 초안·게시한 일기는 건드리지 않는다.
 * dev 서버는 app.scheduling.enabled=false 로 꺼진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DiaryScheduler {

    private final DiaryService service;

    @Scheduled(cron = "0 10 1 * * *", zone = "Asia/Seoul")
    public void dawnDraft() {
        LocalDate yesterday = LocalDate.now(DailyAnalysisService.KST).minusDays(1);
        try {
            service.generate(yesterday, false).ifPresentOrElse(
                    d -> log.info("일기 초안 생성 {}", yesterday),
                    () -> log.info("일기 초안: {} 메모 없음 또는 변경 없음, 건너뜀", yesterday));
        } catch (Exception e) {
            log.warn("일기 초안 배치 실패 {}: {}", yesterday, e.getMessage());
        }
    }
}

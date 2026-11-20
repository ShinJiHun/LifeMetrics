package com.lifemetrics.backend.ptsheet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 01:00 PT 운동일지 시트 → 운동 기록 동기화. 01:10 일기 초안(DiaryScheduler)이 전날 운동을 읽기 전에 돈다.
 * dev 서버는 app.scheduling.enabled=false 로 꺼진다(화면의 [기록 불러오기]로 수동 실행).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PtSheetScheduler {

    private final PtSheetSyncService service;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void dawnSync() {
        try {
            service.sync();
        } catch (Exception e) {
            log.warn("PT 시트 새벽 동기화 실패: {}", e.getMessage());
        }
    }
}

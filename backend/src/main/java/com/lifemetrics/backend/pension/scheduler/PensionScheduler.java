package com.lifemetrics.backend.pension.scheduler;

import com.lifemetrics.backend.pension.service.PensionSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 개발 서버 등에서 app.scheduling.enabled=false 로 끄면 자동 동기화가 돌지 않는다(기본 true).
@Slf4j
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class PensionScheduler {

    @Autowired
    private PensionSyncService pensionSyncService;

    /**
     * 연금복권 매주 금요일 20시에 자동 동기화
     * (연금복권 추첨은 금요일 20:00 이후)
     */
    @Scheduled(cron = "0 0 20 ? * FRI")
    public void schedulePensionSync() {
        log.info("🎰 Starting scheduled pension lottery sync... (Every Friday 20:00 KST)");
        pensionSyncService.syncAll();
        log.info("✅ Pension lottery sync scheduled task completed");
    }
}

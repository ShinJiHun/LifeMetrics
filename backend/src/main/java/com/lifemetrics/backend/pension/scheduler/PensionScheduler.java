package com.lifemetrics.backend.pension.scheduler;

import com.lifemetrics.backend.pension.service.PensionSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
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

    /**
     * 테스트용: 2분마다 로그 확인
     * 배포 후 확인 후 주석처리 또는 삭제 가능
     */
    @Scheduled(cron = "0 */2 * * * *")
    public void testSchedulerLog() {
        log.info("✅ PensionScheduler is working! Real sync runs Friday 20:00 KST");
        log.info("📅 Current time: {}", new java.util.Date());
    }
}

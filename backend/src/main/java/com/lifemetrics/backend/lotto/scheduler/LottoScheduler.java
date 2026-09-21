package com.lifemetrics.backend.lotto.scheduler;

import com.lifemetrics.backend.lotto.service.LottoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LottoScheduler {

    @Autowired
    private LottoSyncService lottoSyncService;

    Logger log = LoggerFactory.getLogger(LottoScheduler.class);

    // 매주 토요일 21:00에 자동 동기화
    @Scheduled(cron = "0 0 21 ? * SAT")
    public void scheduleLottoSync() {
        log.info("🎰 Starting scheduled lotto sync... (Every Saturday 21:00 KST)");
        lottoSyncService.syncAll();
    }
}
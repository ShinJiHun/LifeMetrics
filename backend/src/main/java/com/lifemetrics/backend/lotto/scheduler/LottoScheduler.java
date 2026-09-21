package com.lifemetrics.backend.lotto.scheduler;

import com.lifemetrics.backend.lotto.service.LottoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 개발 서버 등에서 app.scheduling.enabled=false 로 끄면 자동 동기화가 돌지 않는다(기본 true).
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
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
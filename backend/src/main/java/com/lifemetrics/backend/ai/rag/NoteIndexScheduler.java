package com.lifemetrics.backend.ai.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 RAG 색인(바뀐 조각만). 키워드 전용이면 외부 호출이 없고, 임베딩을 켰으면 바뀐 조각만 Gemini 로 보낸다.
 * 다른 스케줄러와 같이 app.scheduling.enabled=false(dev)면 돌지 않는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class NoteIndexScheduler {

    private final NoteIndexController controller;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void nightly() {
        controller.start();
    }
}

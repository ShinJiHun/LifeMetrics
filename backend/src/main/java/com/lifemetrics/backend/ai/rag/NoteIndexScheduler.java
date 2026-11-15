package com.lifemetrics.backend.ai.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 RAG 색인(바뀐 조각만 임베딩). 진료 기록을 외부 임베딩 API 로 보내므로 기본은 꺼져 있다.
 * Gemini 키의 프로젝트를 유료 등급으로 확인한 뒤 rag.index.enabled=true (RAG_INDEX_ENABLED) 로 켠다.
 * dev 에서는 켜지 않는다(수동 POST /api/athlete/notes/reindex 로 시험).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.index.enabled", havingValue = "true")
public class NoteIndexScheduler {

    private final NoteIndexController controller;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void nightly() {
        controller.start();
    }
}

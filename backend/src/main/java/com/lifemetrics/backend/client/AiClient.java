package com.lifemetrics.backend.client;

import com.lifemetrics.backend.dto.AiAnalysisResponse;

/**
 * AI 클라이언트 인터페이스
 */
public interface AiClient {
    
    /**
     * 프롬프트를 보내고 분석 결과를 받음
     */
    AiAnalysisResponse analyze(String prompt);
    
    /**
     * 제공자 타입 반환
     */
    AiProvider getProvider();
}
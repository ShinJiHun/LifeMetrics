package com.lifemetrics.backend.service;

import com.lifemetrics.backend.client.AiProvider;
import com.lifemetrics.backend.domain.GoalType;

/**
 * 체형 분석 서비스 인터페이스
 */
public interface BodyAnalysisService {
    
    /**
     * 인바디 데이터를 분석하고 결과를 업데이트
     *
     * @param userId       사용자 ID
     * @param bodyRecordId 인바디 기록 ID
     * @param goalType     목표 타입
     * @param provider     AI 제공자
     */
    void analyzeAndUpdate(Long userId, Long bodyRecordId, GoalType goalType, AiProvider provider);
}
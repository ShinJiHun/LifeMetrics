package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalysisRequest {
    private String analysisType;  // activity, exercise_session, body, monthly_riding 등
    private Long targetId;        // 단일 분석 시
    private String targetPeriod;  // 기간 분석 시 (2024-01)
}
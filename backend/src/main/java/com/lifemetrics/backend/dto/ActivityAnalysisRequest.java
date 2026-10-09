package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 라이딩 AI 분석 요청 (선택).
 * 라이더가 직접 적은 주관적 컨디션(피로, 수면, 통증, 체감 강도 등)을 함께 넘긴다.
 */
@Getter
@Setter
public class ActivityAnalysisRequest {
    private String condition;
}

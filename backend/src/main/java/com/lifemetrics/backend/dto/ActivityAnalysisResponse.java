// dto/ActivityAnalysisResponse.java
package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 라이딩 AI 분석 응답.
 * <p>
 * 라이덕/STRANK 수준의 코칭 분석 결과를 담는 확장된 스키마.
 */
@Data
@Builder
public class ActivityAnalysisResponse {

    private Long id;
    private Long activityId;
    private String model;
    private String createdAt;

    // ── 핵심 ───────────────────────────────────────────────
    /** 한줄 진단 (40자 이내) */
    private String summary;

    /** 강도 라벨: 낮음/보통/높음/매우높음 */
    private String intensity;

    /** 종합 점수 0~100 */
    private Integer score;

    // ── 정량 분석 (신규) ───────────────────────────────────
    /** 페이싱 평가: 양호/불균형/후반약화/전반과속 */
    private String pacingGrade;

    /** 추정 IF (Intensity Factor, 0.5~1.5 범위) */
    private Double intensityFactor;

    /** 추정 TSS (Training Stress Score) */
    private Integer trainingStress;

    // ── 코칭 인사이트 (신규) ────────────────────────────────
    /** 페이싱 분석 (60자 이내) */
    private String pacingAnalysis;

    /** 심박/파워 분석 (60자 이내) */
    private String physiologyAnalysis;

    /** 케이던스 분석 (60자 이내) */
    private String cadenceAnalysis;

    /** 날씨 영향 분석 (60자 이내) */
    private String weatherImpact;

    // ── 기존 ───────────────────────────────────────────────
    /** 잘한 점 3가지 */
    private List<String> highlights;

    /** 개선점 2~3가지 */
    private List<String> suggestions;

    // ── 처방 (신규) ─────────────────────────────────────────
    /** 다음 48시간 추천 (회복/유지/자극) */
    private String recoveryAdvice;

    /** 다음 라이딩 처방 (80자 이내) */
    private String nextRideTip;
}

package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BrevetAnalysisResponse {
    private Long id;
    private String eventName;
    private String eventDate;
    private String createdAt;

    // Claude 분석 결과
    private String summary;           // 한줄 요약
    private String overallCondition;  // 전체 컨디션 평가 (좋음/보통/어려움)
    private String paceStrategy;      // 페이스 전략
    private List<String> warnings;    // 주의사항
    private List<String> tips;        // 구간별 팁
    private String conclusion;        // 종합 조언
    private Integer score;            // 완주 가능성 점수 (0~100)
}

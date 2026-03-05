package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class MonthlyAnalysisResponse {
    private Long id;
    private String period;            // 2024-01
    private String model;
    private String createdAt;
    
    // 분석 데이터
    private String summary;
    private String overallTrend;      // 개선/유지/하락
    
    // 라이딩 요약
    private Integer ridingCount;
    private Double ridingDistanceKm;
    private String ridingAssessment;
    
    // 헬스 요약
    private Integer exerciseCount;
    private Integer totalSets;
    private String exerciseAssessment;
    
    // 체성분 요약
    private Double weightChange;
    private Double bodyFatChange;
    private String bodyAssessment;
    
    private List<String> achievements;
    private List<String> nextGoals;
    private Integer score;
}
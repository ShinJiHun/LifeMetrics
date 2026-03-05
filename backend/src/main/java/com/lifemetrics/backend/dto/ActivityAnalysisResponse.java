package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class ActivityAnalysisResponse {
    private Long id;
    private Long activityId;
    private String model;
    private String createdAt;
    
    // 분석 데이터
    private String summary;           // 한 줄 요약
    private String intensity;         // 낮음/보통/높음/매우높음
    private List<String> highlights;  // 잘한 점
    private List<String> suggestions; // 개선점
    private Integer score;            // 0-100
}
package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class ExerciseAnalysisResponse {
    private Long id;
    private Long sessionId;
    private String model;
    private String createdAt;

    // 분석 데이터
    private String summary;
    private String targetMuscles;      // 주요 타겟 부위
    private String volumeLevel;        // 볼륨 평가 (낮음/적정/높음)
    private String intensityLevel;     // 강도 평가
    private List<String> highlights;
    private List<String> suggestions;
    private Integer score;
}
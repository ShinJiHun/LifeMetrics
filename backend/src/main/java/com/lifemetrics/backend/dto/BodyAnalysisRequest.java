package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BodyAnalysisRequest {

    private Long userId;
    private Long bodyRecordId;

    private String goalType;      // DIET / MAINTAIN / BULK
    private String modelProvider; // openai / ollama
    private String modelName;

    // 인바디 핵심 수치
    private Double weight;
    private Double skeletalMuscleMass;
    private Double bodyFatMass;
    private Double bodyFatPercentage;
    private Double bmi;
    private Integer visceralFatLevel;

    // 최근 변화량
    private Double weightDelta;
    private Double skeletalMuscleMassDelta;
    private Double bodyFatPercentageDelta;
}

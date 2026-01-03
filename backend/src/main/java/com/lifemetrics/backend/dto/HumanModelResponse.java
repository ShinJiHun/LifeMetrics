package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HumanModelResponse {

    // 🔑 모델 선택용 (프론트 핵심 필드)
    private String bodyType;   // lean | normal | fit | athlete | overweight | obese

    // =====================
    // 원본 신체 정보
    // =====================
    private Double height;
    private Double weight;
    private String gender;

    private Double skeletalMuscleMass;
    private Double bodyFatMass;
    private Double bodyFatPercentage;

    // =====================
    // 3D 모델 변형 파라미터
    // =====================
    private Double muscleScale;
    private Double fatScale;
    private Double waistScale;

    private String recordDate;
}

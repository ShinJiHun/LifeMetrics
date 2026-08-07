// BodyRecordResponse.java
package com.lifemetrics.backend.dto;

import com.lifemetrics.backend.entity.MeasurementType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BodyRecordResponse {

    private LocalDate recordDate;
    private MeasurementType measurementType;

    // ===== 공통 =====
    private Double weight;
    private Double bmi;
    private Double bodyFatPercentage;
    private Double bodyFatMass;
    private Double fatFreeMass;
    private Double fatFreeMassDelta;

    // ===== INBODY 전용 =====
    private Double skeletalMuscleMass;
    private Integer visceralFatLevel;

    // ===== FITDAYS 전용 =====
    private Double bodyWater;
    private Double boneMass;
    private Double basalMetabolicRate;

    // ===== delta =====
    private Double weightDelta;
    private Double skeletalMuscleMassDelta;
    private Double bodyFatPercentageDelta;

    // ===== LLM 원문 JSON =====
    private String rawLlmJson;
}

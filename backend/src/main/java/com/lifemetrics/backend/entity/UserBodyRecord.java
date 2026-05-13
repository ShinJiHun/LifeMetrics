package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "user_body_record")
@Getter
@Setter
public class UserBodyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate recordDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeasurementType measurementType;

    // ===== 공통 =====
    private Double weight;
    private Double bmi;
    private Double bodyFatPercentage;
    private Double bodyFatMass;

    // ===== INBODY 전용 =====
    private Double skeletalMuscleMass;
    private Integer visceralFatLevel;
    private Double waistCircumference;
    private Double thighCircumference;
    private Double chestCircumference;

    // ===== FITDAYS 전용 =====
    @Column(name = "total_body_water")
    private Double bodyWater;

    @Column(name = "protein")
    private Double proteinMass;

    private Double mineral;
    private Double boneMass;
    private Double basalMetabolicRate;

    @Column(name = "raw_llm_json", columnDefinition = "longtext")
    private String rawLlmJson;

}

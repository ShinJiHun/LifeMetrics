package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /**
     * 제지방량(kg). 인바디 기록지에 인쇄되는 값이라 파서가 추출하면 그 값이 들어간다.
     * 기존 기록은 체중 - 체지방량으로 backfill 했다(항등식이라 기록지 값과 일치).
     */
    private Double fatFreeMass;

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

    /** NAS 에 저장된 원본 기록지 이미지 파일명. 인바디 재추출이 이 값으로 이미지를 다시 읽는다. */
    private String rawFilename;

    // ===== 기록지 재추출로만 채워지는 값 (계산으로 유도할 수 없다) =====
    private Integer inbodyScore;
    private Double targetWeight;
    private Double weightControl;
    private Double fatControl;
    private Double muscleControl;
    private Double obesityDegree;
    private Double waistHipRatio;
    private Integer recommendedIntakeKcal;

    /** 부위별 근육/체지방 등급. 기록지가 수치가 아닌 등급만 주는 경우가 많아 JSON 으로 둔다. */
    @Column(name = "segmental_json", columnDefinition = "text")
    private String segmentalJson;

    private LocalDateTime reextractedAt;

    @Column(name = "raw_llm_json", columnDefinition = "longtext")
    private String rawLlmJson;

}

package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 라이더 신체 치수(피팅용). 측정일별로 쌓아 변화를 본다. 길이는 mm(키만 cm).
 */
@Entity
@Table(name = "rider_measurement")
@Getter
@Setter
@NoArgsConstructor
public class RiderMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId = 1L;

    @Column(name = "measure_date", nullable = false)
    private LocalDate measureDate;

    @Column(name = "measured_by")
    private String measuredBy;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "inseam_mm")
    private Double inseamMm;

    @Column(name = "torso_mm")
    private Double torsoMm;

    @Column(name = "arm_mm")
    private Double armMm;

    @Column(name = "shoulder_width_mm")
    private Double shoulderWidthMm;

    @Column(name = "sit_bone_width_mm")
    private Double sitBoneWidthMm;

    @Column(name = "foot_length_left_mm")
    private Double footLengthLeftMm;

    @Column(name = "foot_length_right_mm")
    private Double footLengthRightMm;

    @Column(name = "foot_width_left_mm")
    private Double footWidthLeftMm;

    @Column(name = "foot_width_right_mm")
    private Double footWidthRightMm;

    @Column(name = "arch_type")
    private String archType;

    @Column(name = "shoe_size_mm")
    private Double shoeSizeMm;

    @Column(name = "shoe_size_eu")
    private Double shoeSizeEu;

    @Column(name = "shoe_model")
    private String shoeModel;

    private String insole;

    private String flexibility;

    /**
     * 스캐너/측정지의 상세 항목 원본. 위 컬럼은 피팅 계산·화면용 핵심 값, 여기는 나머지 전부.
     * {"unit": "mm", "items": [{"item": "Foot length", "left": 252, "right": 256}, {"item": "...", "value": n}]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "json")
    private Map<String, Object> detailJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 자전거 피팅 기록. fitDate 부터 이 세팅이 적용된 것으로 본다.
 * 길이는 mm, 각도는 도(°).
 */
@Entity
@Table(name = "bike_fit")
@Getter
@Setter
@NoArgsConstructor
public class BikeFit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bike_id", nullable = false)
    private Long bikeId;

    @Column(name = "fit_date", nullable = false)
    private LocalDate fitDate;

    private String fitter;

    @Column(name = "saddle_height_mm")
    private Double saddleHeightMm;

    @Column(name = "saddle_setback_mm")
    private Double saddleSetbackMm;

    @Column(name = "saddle_tilt_deg")
    private Double saddleTiltDeg;

    @Column(name = "saddle_model")
    private String saddleModel;

    @Column(name = "reach_mm")
    private Double reachMm;

    @Column(name = "drop_mm")
    private Double dropMm;

    @Column(name = "stem_length_mm")
    private Double stemLengthMm;

    @Column(name = "stem_angle_deg")
    private Double stemAngleDeg;

    @Column(name = "spacer_mm")
    private Double spacerMm;

    @Column(name = "handlebar_width_mm")
    private Double handlebarWidthMm;

    @Column(name = "crank_length_mm")
    private Double crankLengthMm;

    @Column(name = "cleat_left")
    private String cleatLeft;

    @Column(name = "cleat_right")
    private String cleatRight;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

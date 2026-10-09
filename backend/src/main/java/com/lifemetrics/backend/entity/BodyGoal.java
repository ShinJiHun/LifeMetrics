package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 체성분 목표. 설정 시점의 시작값과 목표값을 고정해 진행률의 기준으로 쓴다.
 * 인바디 기록지의 목표 체중은 측정마다 바뀌어 최신 기록을 그대로 쓰면 기준이 움직인다.
 * status 가 ACTIVE 인 목표는 사용자당 하나만 둔다(새 목표 저장 시 기존 ACTIVE 는 ABANDONED).
 */
@Entity
@Table(name = "body_goal")
@Getter
@Setter
@NoArgsConstructor
public class BodyGoal {

    public static final String ACTIVE = "ACTIVE";
    public static final String ACHIEVED = "ACHIEVED";
    public static final String ABANDONED = "ABANDONED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String status;

    /** INBODY(기록지 조절값) / FORMULA(계산) / MANUAL(직접 수정) */
    @Column(nullable = false)
    private String source;

    @Column(name = "base_record_id")
    private Long baseRecordId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "start_weight")
    private Double startWeight;

    @Column(name = "start_fat_mass")
    private Double startFatMass;

    @Column(name = "start_muscle_mass")
    private Double startMuscleMass;

    @Column(name = "target_weight")
    private Double targetWeight;

    @Column(name = "target_fat_mass")
    private Double targetFatMass;

    @Column(name = "target_muscle_mass")
    private Double targetMuscleMass;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

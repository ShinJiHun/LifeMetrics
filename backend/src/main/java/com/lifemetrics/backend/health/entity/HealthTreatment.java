package com.lifemetrics.backend.health.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 치료·부상 기록. 관리자가 직접 적고, 분석(일일·라이딩·웨이트 세션)이 그 날짜에 진행 중이던 치료를 함께 본다.
 * endDate 가 null 이면 치료 중이다.
 */
@Entity
@Table(name = "health_treatment")
@Getter
@Setter
@NoArgsConstructor
public class HealthTreatment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 진단명/부상명 */
    @Column(nullable = false)
    private String name;

    @Column(name = "body_part")
    private String bodyPart;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** null 이면 치료 중 */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 운동 제한·권고 */
    @Column(columnDefinition = "TEXT")
    private String restrictions;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

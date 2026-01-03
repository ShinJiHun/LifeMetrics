package com.lifemetrics.backend.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

import java.time.LocalDate;

@Entity
@Table(name = "user_inbody_record")
@Getter
@Setter
public class UserInbodyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate recordDate;

    private Double weight;
    private Double skeletalMuscleMass;
    private Double bodyFatMass;
    private Double bodyFatPercentage;

    @Column(name = "bmi")
    private Double bmi;

    private Integer visceralFatLevel;

    private Boolean isMeasured;

    // ===== 계산 필드 =====
    @Transient
    private Double weightDelta;

    @Transient
    private Double skeletalMuscleMassDelta;

    @Transient
    private Double bodyFatPercentageDelta;
}

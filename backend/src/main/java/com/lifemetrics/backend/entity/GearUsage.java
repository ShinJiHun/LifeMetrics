// entity/GearUsage.java
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "gear_usage")
@Getter
@Setter
public class GearUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_core_id")
    private Long activityCoreId;

    @Column(name = "front_gear")
    private Integer frontGear;

    @Column(name = "rear_gear")
    private Integer rearGear;

    @Column(name = "gear_ratio")
    private Double gearRatio;

    private String terrain;

    @Column(name = "duration_sec")
    private Double durationSec;

    private Double distance;

    @Column(name = "avg_power")
    private Double avgPower;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}

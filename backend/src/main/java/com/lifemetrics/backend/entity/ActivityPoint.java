// entity/ActivityPoint.java
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_point")
@Getter
@IdClass(ActivityPointId.class)
public class ActivityPoint {
    @Id
    @Column(name = "activity_core_id")
    private Long activityCoreId;

    @Id
    private Integer seq;

    @Column(name = "point_time")
    private LocalDateTime pointTime;

    private Double lat;
    private Double lon;
    private Double altitude;
    private Double distance;
    private Double speed;
    private Double cadence;

    @Column(name = "heart_rate")
    private Double heartRate;

    private Double power;
    private Double slope;
    private Double heading;
}
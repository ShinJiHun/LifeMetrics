// entity/ActivityCore.java
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_core")
@Getter
public class ActivityCore {
    @Id
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String filename;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "total_distance")
    private Double totalDistance;

    @Column(name = "moving_time")
    private Integer movingTime;

    @Column(name = "elapsed_time")
    private Integer elapsedTime;

    @Column(name = "total_ascent")
    private Double totalAscent;

    @Column(name = "total_descent")
    private Double totalDescent;

    @Column(name = "avg_speed")
    private Double avgSpeed;

    @Column(name = "max_speed")
    private Double maxSpeed;

    @Column(name = "avg_cadence")
    private Double avgCadence;

    @Column(name = "avg_heart_rate")
    private Double avgHeartRate;

    @Column(name = "max_heart_rate")
    private Double maxHeartRate;

    @Column(name = "avg_power")
    private Double avgPower;

    @Column(name = "max_power")
    private Double maxPower;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lon")
    private Double startLon;

    @Column(name = "end_lat")
    private Double endLat;

    @Column(name = "end_lon")
    private Double endLon;

    @Column(columnDefinition = "longtext")
    private String polyline;

    @Column(name = "gear_name")
    private String gearName;

    @Column(name = "uphill_distance")
    private Double uphillDistance;

    @Column(name = "flat_distance")
    private Double flatDistance;

    @Column(name = "down_distance")
    private Double downDistance;

    @Column(name = "calories")
    private int calories;
}
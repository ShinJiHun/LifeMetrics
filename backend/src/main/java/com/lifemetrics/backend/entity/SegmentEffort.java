package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "segment_effort")
@Getter
@Setter
public class SegmentEffort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "segment_id")
    private Long segmentId;

    @Column(name = "activity_core_id")
    private Long activityCoreId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "elapsed_time_sec")
    private Integer elapsedTimeSec;

    @Column(name = "moving_time_sec")
    private Integer movingTimeSec;

    @Column(name = "match_percent")
    private Double matchPercent;

    @Column(name = "start_distance_m")
    private Double startDistanceM;

    @Column(name = "end_distance_m")
    private Double endDistanceM;

    @Column(name = "avg_speed")
    private Double avgSpeed;

    @Column(name = "max_speed")
    private Double maxSpeed;

    @Column(name = "avg_power")
    private Double avgPower;

    @Column(name = "max_power")
    private Double maxPower;

    @Column(name = "avg_heart_rate")
    private Integer avgHeartRate;

    @Column(name = "max_heart_rate")
    private Integer maxHeartRate;

    @Column(name = "avg_cadence")
    private Integer avgCadence;

    @Column(name = "start_point_seq")
    private Integer startPointSeq;

    @Column(name = "end_point_seq")
    private Integer endPointSeq;

    @Column(name = "pr_rank")
    private Integer prRank;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

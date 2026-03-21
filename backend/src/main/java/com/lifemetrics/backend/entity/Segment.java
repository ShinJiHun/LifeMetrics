package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "segment")
@Getter
@Setter
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lon")
    private Double startLon;

    @Column(name = "end_lat")
    private Double endLat;

    @Column(name = "end_lon")
    private Double endLon;

    @Column(name = "start_radius_m")
    private Integer startRadiusM;

    @Column(name = "end_radius_m")
    private Integer endRadiusM;

    @Column(name = "match_tolerance_m")
    private Integer matchToleranceM;

    @Column(name = "min_match_percent")
    private Double minMatchPercent;

    private Double distance;

    @Column(name = "elevation_gain")
    private Double elevationGain;

    @Column(name = "elevation_loss")
    private Double elevationLoss;

    @Column(name = "avg_grade")
    private Double avgGrade;

    @Column(name = "max_grade")
    private Double maxGrade;

    @Column(name = "direction_degrees")
    private Double directionDegrees;

    @Column(name = "bbox_min_lat")
    private Double bboxMinLat;

    @Column(name = "bbox_max_lat")
    private Double bboxMaxLat;

    @Column(name = "bbox_min_lon")
    private Double bboxMinLon;

    @Column(name = "bbox_max_lon")
    private Double bboxMaxLon;

    @Column(columnDefinition = "TEXT")
    private String polyline;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "strava_segment_id")
    private Long stravaSegmentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

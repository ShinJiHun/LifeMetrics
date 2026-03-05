package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// dto/ActivitySummaryDto.java
@Getter
@Builder
public class ActivitySummaryDto {
    private Long id;
    private String filename;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double totalDistance;
    private Integer movingTime;
    private Integer elapsedTime;
    private Double avgSpeed;
    private Double maxSpeed;
    private Double totalAscent;
    private Double totalDescent;
    private String gearName;
    private Double startLat;
    private Double startLon;
    private Double endLat;
    private Double endLon;
    private String polyline;  // 추가!
}
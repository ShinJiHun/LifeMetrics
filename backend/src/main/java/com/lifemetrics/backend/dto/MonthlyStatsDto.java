// dto/MonthlyStatsDto.java
package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthlyStatsDto {
    private int year;
    private int month;
    private int rideCount;
    private Double totalDistance;
    private Integer totalMovingTime;
    private Double totalAscent;
    private Double avgSpeed;
}
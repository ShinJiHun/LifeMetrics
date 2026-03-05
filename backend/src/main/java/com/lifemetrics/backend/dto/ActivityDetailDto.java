// dto/ActivityDetailDto.java
package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ActivityDetailDto {
    private Long id;
    private String filename;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 거리/시간
    private Double totalDistance;
    private Integer movingTime;
    private Integer elapsedTime;

    // 속도
    private Double avgSpeed;
    private Double maxSpeed;

    // 고도
    private Double totalAscent;
    private Double totalDescent;

    // 심박/파워/케이던스
    private Double avgHeartRate;
    private Double maxHeartRate;
    private Double avgPower;
    private Double maxPower;
    private Double avgCadence;

    // 거리 구분
    private Double uphillDistance;
    private Double flatDistance;
    private Double downDistance;

    // 위치
    private Double startLat;
    private Double startLon;
    private Double endLat;
    private Double endLon;
    private String polyline;

    // 장비
    private String gearName;

    // 날씨
    private WeatherDto weather;

    // 포인트 데이터 (차트용)
    private List<ActivityPointDto> points;
}
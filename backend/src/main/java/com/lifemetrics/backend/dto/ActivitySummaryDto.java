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

    // ★ 라이드 제목 (사용자 입력 또는 자동 생성)
    private String name;

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
    private String polyline;
    private Double avgHeartRate;
    private Double maxHeartRate;
    private Double avgPower;
    private Double maxPower;
    private Boolean hasPower;
    private Double avgCadence;
    private Double maxCadence;          // ★ 추가 (Avg/Max 테이블용)
    private Integer calories;

    // ★ 출발지명 (역지오코딩 결과, 예: "천호동, 서울")
    private String locationName;

    // ★ Strava 스타일 Relative Effort (TRIMP 기반)
    private Integer relativeEffort;

    // ★ 날씨 (요약 페이지에서도 보여주려면 추가)
    private WeatherDto weather;
}

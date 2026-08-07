// dto/ActivityDetailDto.java
package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

@Getter
@Builder
public class ActivityDetailDto {
    private Long id;
    private String filename;

    // ★ 라이드 제목 (사용자 입력 또는 자동 생성)
    private String name;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private LocalDateTime startTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
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
    private Double maxCadence;          // ★ 추가

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

    // ★ 출발지명 (역지오코딩 결과, 예: "천호동, 서울")
    private String locationName;

    // ★ Strava 스타일 Relative Effort (TRIMP 기반)
    private Integer relativeEffort;

    // 칼로리
    private Integer calories;

    // 자전거 / 구동계 (활동일 기준 effective 컴포넌트)
    private GearContext gearContext;

    // 날씨
    private WeatherDto weather;

    // 포인트 데이터 (차트용)
    private List<ActivityPointDto> points;
}

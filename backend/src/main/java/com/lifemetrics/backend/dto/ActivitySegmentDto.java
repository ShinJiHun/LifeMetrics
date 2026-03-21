package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ActivitySegmentDto {
    private Long effortId;
    private Long segmentId;
    private String segmentName;
    private Double distance;       // 세그먼트 거리 (m)
    private Double elevationGain;  // 고도 획득 (m)
    private Double avgGrade;       // 평균 경사 (%)
    private String polyline;       // 세그먼트 경로

    private LocalDateTime startTime;
    private Integer elapsedTimeSec;  // 소요 시간 (초)
    private Integer movingTimeSec;
    private Double avgSpeed;
    private Double maxSpeed;
    private Integer avgHeartRate;
    private Integer maxHeartRate;
    private Double avgPower;
    private Double avgCadence;
    private Double startDistanceM;   // 활동 내 시작 거리
    private Double endDistanceM;     // 활동 내 종료 거리
    private Integer prRank;          // PR 순위 (1이면 PR)
}

package com.lifemetrics.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivitySegmentDto {
    private Long effortId;
    private Long segmentId;
    private String segmentName;
    private Double distance;
    private Double elevationGain;
    private Double avgGrade;
    private String polyline;

    private LocalDateTime startTime;
    private Integer elapsedTimeSec;
    private Integer movingTimeSec;
    private Double avgSpeed;
    private Double maxSpeed;
    private Integer avgHeartRate;
    private Integer maxHeartRate;
    private Double avgPower;
    private Double avgCadence;
    private Double startDistanceM;
    private Double endDistanceM;
    private Integer prRank;

    // 계층 구조용
    private Integer depth;              // 0=최상위, 1=자식, 2=손자
    private List<ActivitySegmentDto> children;  // 자식 세그먼트
    private Double maxPower;
}

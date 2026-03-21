package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SegmentPredictResponse {
    private String eventName;
    private String startTime;
    private int totalPredictedMinutes;   // 총 예상 시간 (분)
    private String totalPredictedTime;   // "16:30" 형식
    private String estimatedFinishTime;  // 완주 예상 시각 (예: "22:30")
    private List<SegmentResultDto> segments;

    @Getter
    @Builder
    public static class SegmentResultDto {
        private int index;
        private String label;              // "0~100km"
        private double distanceKm;
        private double ascentM;

        // 유사 기록
        private Long similarActivityId;
        private String similarDate;        // "2025-11-22"
        private double similarDistanceKm;
        private double similarAscentM;
        private double similarityScore;    // 낮을수록 유사

        // 예상 시간
        private int predictedMinutes;      // 예상 소요 시간 (분)
        private String predictedTime;      // "5:30" 형식
        private double predictedAvgSpeed;  // 예상 평속 (km/h)

        // 보정 평속
        private double adjustedSpeed;      // 고도보정 평속
    }
}

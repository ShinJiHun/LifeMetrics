package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SegmentPredictRequest {
    private Long userId = 1L;
    private String eventName;       // 대회명 (표시용)
    private String startTime;       // 출발 시간 (예: "06:00")
    private List<SegmentDto> segments;

    @Getter
    @Setter
    public static class SegmentDto {
        private int index;           // 구간 번호 (0부터)
        private double distanceKm;   // 구간 거리 (km)
        private double ascentM;      // 구간 획득 고도 (m)
        private String label;        // 표시용 (예: "0~100km")
    }
}

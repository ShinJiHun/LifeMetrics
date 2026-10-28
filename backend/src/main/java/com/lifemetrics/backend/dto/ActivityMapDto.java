package com.lifemetrics.backend.dto;

import java.time.LocalDateTime;

/** 라이딩 지도(전체 기록) 한 건. polyline 은 지도 전체 보기용으로 단순화된 값. */
public record ActivityMapDto(
        Long id,
        String name,
        LocalDateTime startTime,
        String rideType,
        Double totalDistance,
        Double totalAscent,
        Integer movingTime,
        String polyline
) {
}

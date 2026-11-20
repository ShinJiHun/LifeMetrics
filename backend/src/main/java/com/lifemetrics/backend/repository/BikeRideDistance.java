package com.lifemetrics.backend.repository;

import java.time.LocalDateTime;

/** 자전거별 라이딩 한 건의 시작 시각과 거리(미터). 소모품 사용 거리 계산용. */
public interface BikeRideDistance {
    Long getBikeId();

    LocalDateTime getStartTime();

    Double getTotalDistance();
}

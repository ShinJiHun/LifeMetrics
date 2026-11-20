package com.lifemetrics.backend.repository;

import java.time.LocalDateTime;

/** 라이딩 한 건의 앞/뒤 톱니수별 주행 거리(미터). */
public interface GearDistanceRow {
    Long getBikeId();

    LocalDateTime getRideStart();

    Integer getFrontGear();

    Integer getRearGear();

    Double getDistance();
}

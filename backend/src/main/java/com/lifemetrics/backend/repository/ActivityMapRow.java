package com.lifemetrics.backend.repository;

import java.time.LocalDateTime;

/** 라이딩 지도용 경량 행. polyline(longtext) 은 빼고 목록·집계에 필요한 값만 읽는다. */
public interface ActivityMapRow {
    Long getId();

    String getName();

    LocalDateTime getStartTime();

    String getRideType();

    /** 미터 */
    Double getTotalDistance();

    /** 미터 */
    Double getTotalAscent();

    /** 초 */
    Integer getMovingTime();
}

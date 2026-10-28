package com.lifemetrics.backend.repository;

/** 라이딩 지도용: 캐시에 없는 기록의 원본 polyline 만 따로 읽는다. */
public interface ActivityPolylineRow {
    Long getId();

    String getPolyline();
}

package com.lifemetrics.backend.repository;

/** 파워미터 라이드 집계. 소비열량 추정의 입력값이다. */
public interface PowerRideStats {
    Long getRideCount();

    Long getMovingSeconds();

    Double getDistanceMeters();

    /** avg_power × moving_time 합계(J). 페달에 실제로 들어간 기계적 일. */
    Double getWorkJoules();

    /** 기기가 기록한 calories 합계(kcal). 참고용 — 기계적 일의 1.3배 수준으로 근거가 불명확하다. */
    Long getReportedCalories();
}

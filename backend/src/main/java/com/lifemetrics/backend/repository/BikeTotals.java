package com.lifemetrics.backend.repository;

/** 자전거별 활동 누적치. bike 테이블의 total_* 컬럼 대신 activity_core 에서 매번 집계해 쓴다. */
public interface BikeTotals {
    Long getBikeId();

    /** moving_time 합계(초). 정지 시간은 제외된다. */
    Long getTotalMovingTime();

    /** elapsed_time 합계(초). 정지·휴식 포함 총 소요시간. */
    Long getTotalElapsedTime();

    /** total_distance 합계(미터). */
    Double getTotalDistance();
}

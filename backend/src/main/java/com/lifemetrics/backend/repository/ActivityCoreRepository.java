package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ActivityCore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ActivityCoreRepository extends JpaRepository<ActivityCore, Long> {

    List<ActivityCore> findByUserIdOrderByStartTimeDesc(Long userId);

    List<ActivityCore> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT a FROM ActivityCore a WHERE a.userId = :userId AND a.startTime >= :start AND a.startTime < :end")
    List<ActivityCore> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @Query("SELECT a FROM ActivityCore a WHERE a.userId = :userId " +
            "AND a.startTime >= :startDate AND a.startTime < :endDate " +
            "ORDER BY a.startTime DESC")
    List<ActivityCore> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(a), SUM(a.totalDistance), SUM(a.movingTime), SUM(a.totalAscent), AVG(a.avgSpeed) " +
            "FROM ActivityCore a WHERE a.userId = :userId " +
            "AND a.startTime >= :startDate AND a.startTime < :endDate")
    Object[] getMonthlyStats(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 자전거별 누적 주행. 이동시간(moving_time)과 총시간(elapsed_time)을 따로 집계한다.
     * bike.total_time / total_distance 컬럼은 갱신되지 않으므로 조회 시점에 여기서 집계한다.
     */
    @Query("SELECT a.bikeId AS bikeId, " +
            "COALESCE(SUM(a.movingTime), 0) AS totalMovingTime, " +
            "COALESCE(SUM(a.elapsedTime), 0) AS totalElapsedTime, " +
            "COALESCE(SUM(a.totalDistance), 0.0) AS totalDistance " +
            "FROM ActivityCore a WHERE a.bikeId IS NOT NULL GROUP BY a.bikeId")
    List<BikeTotals> sumTotalsByBike();

    /**
     * 파워미터가 달린 라이드만 모아 소비열량 추정에 쓸 통계를 낸다.
     *
     * has_power = false 인 기록은 avg_power 가 2000~3800W 로 깨져 있어 반드시 제외해야 한다.
     * 상·하한(30~400W)은 has_power 가 true 인데도 값이 튀는 경우에 대한 안전장치다.
     */
    @Query("SELECT COUNT(a) AS rideCount, " +
            "COALESCE(SUM(a.movingTime), 0) AS movingSeconds, " +
            "COALESCE(SUM(a.totalDistance), 0.0) AS distanceMeters, " +
            "COALESCE(SUM(a.avgPower * a.movingTime), 0.0) AS workJoules, " +
            "COALESCE(SUM(a.calories), 0) AS reportedCalories " +
            "FROM ActivityCore a " +
            "WHERE a.userId = :userId AND a.hasPower = true " +
            "AND a.avgPower BETWEEN 30 AND 400 AND a.movingTime > 0")
    PowerRideStats getPowerRideStats(@Param("userId") Long userId);

    /**
     * 거리/고도 유사도 기반 상위 N개 기록 조회
     * 유사도 = 거리차이비율 * 0.4 + 고도차이비율 * 0.6
     * 최소 거리 50km 이상만 대상
     */
    @Query("""
           SELECT a FROM ActivityCore a
           WHERE a.userId = :userId
             AND a.totalDistance >= 50000
             AND a.totalAscent IS NOT NULL
             AND a.totalAscent > 0
             AND a.elapsedTime > 0
           ORDER BY (
               ABS(a.totalDistance / 1000.0 - :distKm) / :distKm * 0.4
             + ABS(a.totalAscent - :ascentM) / :ascentM * 0.6
           ) ASC
           """)
    List<ActivityCore> findSimilarActivities(
            @Param("userId") Long userId,
            @Param("distKm") double distKm,
            @Param("ascentM") double ascentM,
            Pageable pageable
    );
}

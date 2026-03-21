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

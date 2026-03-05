// repository/ActivityCoreRepository.java
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

    // 기존 findByUserIdAndDateRange가 @Query로 되어있다면 pageable 파라미터 추가
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
}
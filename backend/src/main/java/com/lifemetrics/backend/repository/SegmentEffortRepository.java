package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.SegmentEffort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SegmentEffortRepository extends JpaRepository<SegmentEffort, Long> {

    List<SegmentEffort> findByActivityCoreId(Long activityCoreId);

    List<SegmentEffort> findBySegmentIdAndUserId(Long segmentId, Long userId);

    Optional<SegmentEffort> findByActivityCoreIdAndSegmentId(Long activityCoreId, Long segmentId);

    // 특정 세그먼트의 PR 기록 조회
    @Query("""
           SELECT se FROM SegmentEffort se
           WHERE se.segmentId = :segmentId
             AND se.userId = :userId
             AND se.prRank = 1
           """)
    Optional<SegmentEffort> findPrBySegmentIdAndUserId(
            @Param("segmentId") Long segmentId,
            @Param("userId") Long userId
    );

    // 특정 세그먼트의 모든 기록 (빠른 순)
    @Query("""
           SELECT se FROM SegmentEffort se
           WHERE se.segmentId = :segmentId
             AND se.userId = :userId
           ORDER BY se.elapsedTimeSec ASC
           """)
    List<SegmentEffort> findBySegmentIdAndUserIdOrderByTime(
            @Param("segmentId") Long segmentId,
            @Param("userId") Long userId
    );

    // 최근 N개 활동의 세그먼트 efforts
    @Query("""
           SELECT se FROM SegmentEffort se
           WHERE se.userId = :userId
           ORDER BY se.startTime DESC
           """)
    List<SegmentEffort> findRecentByUserId(@Param("userId") Long userId);

    List<SegmentEffort> findBySegmentIdOrderByElapsedTimeSecAsc(Long segmentId);
}

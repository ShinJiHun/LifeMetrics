package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserBodyRecordRepository extends JpaRepository<UserBodyRecord, Long> {

    List<UserBodyRecord> findByUserIdOrderByRecordDate(Long userId);

    List<UserBodyRecord> findByUserIdAndMeasurementTypeOrderByRecordDate(Long userId, MeasurementType type);

    Optional<UserBodyRecord> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);

    Optional<UserBodyRecord> findTopByUserIdOrderByRecordDateDesc(Long userId);

    /** 감량 분석은 체지방량이 있는 인바디 측정만 쓴다. FitDays 체중계는 체성분 정확도가 떨어진다. */
    Optional<UserBodyRecord> findTopByUserIdAndMeasurementTypeOrderByRecordDateDesc(
            Long userId, MeasurementType measurementType);

    /** 직전 측정 1건만 조회. LIMIT 1이 없으면 과거 기록이 여러 건일 때 NonUniqueResultException이 발생한다. */
    @Query("""
        SELECT r FROM UserBodyRecord r
        WHERE r.userId = :userId
          AND r.recordDate < :date
          AND r.measurementType = :type
        ORDER BY r.recordDate DESC
        LIMIT 1
        """)
    Optional<UserBodyRecord> findPrevious(
        @Param("userId") Long userId,
        @Param("date") LocalDate date,
        @Param("type") MeasurementType type
    );

}
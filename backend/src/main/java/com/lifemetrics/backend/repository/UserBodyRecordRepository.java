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

    // UserBodyRecordRepository.java에 추가
    @Query("""
        SELECT r FROM UserBodyRecord r
        WHERE r.userId = :userId
          AND r.recordDate < :date
          AND r.measurementType = :type
        ORDER BY r.recordDate DESC
        """)
    Optional<UserBodyRecord> findPrevious(
        @Param("userId") Long userId,
        @Param("date") LocalDate date,
        @Param("type") MeasurementType type
    );
}

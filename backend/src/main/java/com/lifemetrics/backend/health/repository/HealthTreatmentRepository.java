package com.lifemetrics.backend.health.repository;

import com.lifemetrics.backend.health.entity.HealthTreatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HealthTreatmentRepository extends JpaRepository<HealthTreatment, Long> {

    /** 치료 중인 것 먼저, 그 안에서 최근 시작한 순 */
    @Query("""
            SELECT t FROM HealthTreatment t
            WHERE t.userId = :userId
            ORDER BY CASE WHEN t.endDate IS NULL THEN 0 ELSE 1 END, t.startDate DESC, t.id DESC
            """)
    List<HealthTreatment> findAllForList(@Param("userId") Long userId);

    /** 그 날짜에 진행 중이던 치료 */
    @Query("""
            SELECT t FROM HealthTreatment t
            WHERE t.userId = :userId
              AND t.startDate <= :date
              AND (t.endDate IS NULL OR t.endDate >= :date)
            ORDER BY t.startDate, t.id
            """)
    List<HealthTreatment> findActiveOn(@Param("userId") Long userId, @Param("date") LocalDate date);
}

package com.lifemetrics.backend.health.repository;

import com.lifemetrics.backend.health.entity.HealthTreatmentVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface HealthTreatmentVisitRepository extends JpaRepository<HealthTreatmentVisit, Long> {

    /** 최근 진료 먼저 */
    List<HealthTreatmentVisit> findByUserIdOrderByVisitDateDescIdDesc(Long userId);

    /** 분석용: 여러 치료의 기간 내 진료, 날짜순 */
    @Query("""
            SELECT v FROM HealthTreatmentVisit v
            WHERE v.treatmentId IN :treatmentIds
              AND v.visitDate BETWEEN :from AND :to
            ORDER BY v.visitDate, v.id
            """)
    List<HealthTreatmentVisit> findForAnalysis(@Param("treatmentIds") Collection<Long> treatmentIds,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /** 같은 문서를 다시 올렸을 때 덮어쓰기용 */
    List<HealthTreatmentVisit> findByTreatmentIdAndVisitDate(Long treatmentId, LocalDate visitDate);

    @Modifying
    @Query("DELETE FROM HealthTreatmentVisit v WHERE v.treatmentId = :treatmentId")
    void deleteByTreatmentId(@Param("treatmentId") Long treatmentId);
}

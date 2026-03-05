
// ============================================
// 2. Repository 추가 메서드 (ExerciseSessionRepository.java)
// ============================================
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ExerciseSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExerciseSessionRepository extends JpaRepository<ExerciseSession, Long> {
    Optional<ExerciseSession> findByUserIdAndSessionDate(Long userId, LocalDate sessionDate);

    @Query("SELECT s FROM ExerciseSession s WHERE s.userId = :userId " +
           "AND s.sessionDate >= :startDate AND s.sessionDate <= :endDate " +
           "ORDER BY s.sessionDate DESC")
    List<ExerciseSession> findByUserIdAndMonth(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

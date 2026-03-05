// ============================================================
// 2. ExerciseHistoryRepository.java - 히스토리 전용 배치 조회
// ============================================================
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ExerciseLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExerciseHistoryRepository extends JpaRepository<ExerciseLog, Long> {

    /**
     * 세션 ID 목록으로 로그 + 운동명 + 카테고리명을 한 번에 조회
     * N+1 → 1번 쿼리로 해결
     */
    @Query("""
        SELECT el, ei.nameKo, ei.nameEn, ec.name
        FROM ExerciseLog el
        JOIN ExerciseItem ei ON el.exerciseItemId = ei.id
        JOIN ExerciseCategory ec ON ei.categoryId = ec.id
        WHERE el.sessionId IN :sessionIds
        ORDER BY el.sessionId, el.id
    """)
    List<Object[]> findLogsWithItemsBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    /**
     * 세션 ID 목록의 모든 세트를 한 번에 조회
     */
    @Query("""
        SELECT es
        FROM ExerciseSet es
        WHERE es.logId IN (
            SELECT el.id FROM ExerciseLog el WHERE el.sessionId IN :sessionIds
        )
        ORDER BY es.logId, es.setNumber
    """)
    List<com.lifemetrics.backend.entity.ExerciseSet> findSetsBySessionIds(
            @Param("sessionIds") List<Long> sessionIds);
}
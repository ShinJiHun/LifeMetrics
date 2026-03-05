package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.domain.GoalType;
import com.lifemetrics.backend.entity.BodyAnalysisLifetimeCurrent;
import com.lifemetrics.backend.entity.BodyAnalysisLifetimeCurrentId;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BodyAnalysisLifetimeCurrentRepository
        extends JpaRepository<BodyAnalysisLifetimeCurrent, BodyAnalysisLifetimeCurrentId> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO body_analysis_lifetime_current
            (user_id, goal_type, analysis_id, model_provider)
        VALUES
            (:userId, :goalType, :analysisId, :provider)
        ON DUPLICATE KEY UPDATE
            analysis_id = VALUES(analysis_id),
            model_provider = VALUES(model_provider),
            updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsert(
            @Param("userId") Long userId,
            @Param("goalType") GoalType goalType,
            @Param("analysisId") Long analysisId,
            @Param("provider") String provider
    );
}

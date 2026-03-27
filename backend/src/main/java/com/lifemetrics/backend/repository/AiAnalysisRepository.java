package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    // 단일 활동 분석 조회
    Optional<AiAnalysis> findByUserIdAndAnalysisTypeAndTargetId(
            Long userId, String analysisType, Long targetId);

    // 기간별 분석 조회
    Optional<AiAnalysis> findByUserIdAndAnalysisTypeAndTargetPeriod(
            Long userId, String analysisType, String targetPeriod);

    Optional<AiAnalysis> findByUserIdAndAnalysisTypeAndTargetIdAndTargetPeriod(
        Long userId, String analysisType, Long targetId, String targetPeriod
    );
}

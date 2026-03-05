package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.BodyAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BodyAnalysisResultRepository
        extends JpaRepository<BodyAnalysisResult, Long> {

    Optional<BodyAnalysisResult>
    findByBodyRecordIdAndGoalType(Long bodyRecordId, String goalType);
}

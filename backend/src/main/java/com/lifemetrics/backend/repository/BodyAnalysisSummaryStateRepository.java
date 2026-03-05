package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.BodyAnalysisSummaryState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyAnalysisSummaryStateRepository
        extends JpaRepository<BodyAnalysisSummaryState, Long> {
}

package com.lifemetrics.backend.service;

import com.lifemetrics.backend.domain.GoalType;
import com.lifemetrics.backend.entity.BodyAnalysisSummaryState;
import com.lifemetrics.backend.entity.UserBodyRecord;

public interface BodyAnalysisPromptService {

    String build(
        GoalType goalType,
        BodyAnalysisSummaryState summary,
        UserBodyRecord prev,
        UserBodyRecord curr
    );
}

package com.lifemetrics.backend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GoalType {

    DIET("다이어트"),
    MAINTAIN("유지"),
    MUSCLE_GAIN("근육 증가"),
    BODY_RECOMPOSITION("체형 개선");

    private final String displayName;
}

package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ExerciseLogRequest {
    private String sessionDate;  // "2025-01-09"
    private Long userId;
    private Boolean isPT;        // PT 여부 추가
    private List<ExerciseEntry> exercises;

    @Getter
    @Setter
    public static class ExerciseEntry {
        private Long exerciseItemId;
        private Integer restTimeSec;  // 휴식 시간 (초) 추가
        private String memo;          // 메모 추가
        private List<SetEntry> sets;
    }

    @Getter
    @Setter
    public static class SetEntry {
        private Integer setNumber;
        private Double weight;
        private Integer reps;
    }
}

package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExerciseHistoryResponse {
    private Long id;
    private String sessionDate;
    private Boolean isPT;
    private String memo;
    private List<ExerciseLogDto> logs;

    // 세션 레벨 칼로리/볼륨 요약
    private Double estimatedCalories;   // 추정 소모 칼로리 (kcal)
    private Double totalVolume;         // 총 볼륨 (kg)
    private Integer totalSets;          // 총 세트 수
    private Integer estimatedMinutes;   // 추정 운동 시간 (분)

    @Getter
    @Builder
    public static class ExerciseLogDto {
        private Long id;
        private Long exerciseItemId;
        private String exerciseNameKo;
        private String exerciseNameEn;
        private String categoryName;
        private Integer restTimeSec;
        private String memo;
        private List<ExerciseSetDto> sets;
        private List<MuscleMappingDto> muscleMappings;
    }

    @Getter
    @Builder
    public static class MuscleMappingDto {
        private Long muscleGroupId;
        private String muscleName;
        private String role;
        private Integer activationLevel;
    }

    @Getter
    @Builder
    public static class ExerciseSetDto {
        private Long id;
        private Integer setNumber;
        private Double weight;
        private Integer reps;
    }
}
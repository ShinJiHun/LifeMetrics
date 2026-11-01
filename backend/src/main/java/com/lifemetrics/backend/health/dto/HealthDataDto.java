package com.lifemetrics.backend.health.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Health Connect 동기화로 저장된 데이터의 조회 응답. 시각은 모두 KST 다. */
public final class HealthDataDto {

    private HealthDataDto() {}

    public record DailyActivity(
            LocalDate date,
            Long steps,
            Double distanceM,
            Double activeKcal,
            Double totalKcal,
            Integer exerciseMinutes,
            Integer sleepMinutes,
            Integer restingHeartRate
    ) {}

    public record ExerciseSession(
            String id,
            String type,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int durationMin,
            Double activeKcal,
            Double distanceM,
            Integer avgHeartRate,
            String sourceApp
    ) {}

    public record NutritionEntry(
            String id,
            LocalDate date,
            String mealType,
            String name,
            LocalDateTime eatTime,
            Double calories,
            Double carbsG,
            Double proteinG,
            Double fatG,
            Double sodiumMg,
            Double sugarG,
            Double fiberG
    ) {}
}

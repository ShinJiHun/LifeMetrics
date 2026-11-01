package com.lifemetrics.backend.health.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 하루치 기록 집계. 에너지 수지는 계산할 근거가 부족하면 null 이고 그 이유는 notes 에 적는다.
 * 칼로리는 kcal, 시각은 KST 다.
 */
public record DailySummary(
        LocalDate date,
        boolean dayFinished,
        Body body,
        Energy energy,
        Activity activity,
        Meals meals,
        List<Ride> rides,
        List<Fitness> fitness,
        List<String> notes
) {

    /** 기초대사량의 근거가 된 그 날짜 이전의 최신 인바디. 기록이 없으면 body 자체가 null 이다. */
    public record Body(
            LocalDate recordDate,
            Double weightKg,
            Double fatFreeMassKg,
            Integer bmrKcal,
            boolean bmrEstimated
    ) {}

    public record Energy(
            Integer intakeKcal,
            Integer bmrKcal,
            Integer phoneActiveKcal,
            Integer extraRideKcal,
            Integer extraFitnessKcal,
            Integer burnedKcal,
            Integer balanceKcal,
            Integer intakePercentOfBmr
    ) {}

    public record Activity(Long steps, Double distanceM, Integer sleepMinutes, Integer restingHeartRate) {}

    public record Meals(
            int calories,
            double carbsG,
            double proteinG,
            double fatG,
            List<String> recordedMeals,
            List<String> missingMeals
    ) {}

    /** includedInPhoneActivity 가 true 면 이 칼로리는 이미 폰 활동 칼로리에 들어 있다. */
    public record Ride(
            Long id,
            String name,
            String rideType,
            LocalDateTime startTime,
            Double distanceM,
            Integer movingTimeSec,
            Double ascentM,
            int kcal,
            boolean includedInPhoneActivity
    ) {}

    public record Fitness(
            Long id,
            Boolean isPT,
            Double totalVolumeKg,
            Integer totalSets,
            Integer estimatedMinutes,
            Double estimatedKcal,
            List<String> categories,
            List<String> exercises
    ) {}
}

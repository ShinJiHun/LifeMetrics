package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.dto.ExerciseHistoryResponse;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.health.dto.DailySummary;
import com.lifemetrics.backend.health.dto.HealthDataDto.DailyActivity;
import com.lifemetrics.backend.health.dto.HealthDataDto.ExerciseSession;
import com.lifemetrics.backend.health.dto.HealthDataDto.NutritionEntry;
import com.lifemetrics.backend.health.service.HealthDataQueryService.DateRange;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import com.lifemetrics.backend.service.ExerciseLogService;
import com.lifemetrics.backend.service.WeightLossAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 하루치 기록을 합치고 인바디 기초대사량 기준으로 에너지 수지를 계산한다.
 * <p>
 * 소모 = 기초대사량 + 폰 활동 칼로리 + 폰에 없는 라이딩·웨이트.
 * 가민 커넥트가 헬스 커넥트로 넘긴 라이딩은 폰 활동 칼로리에 이미 들어 있으므로 다시 더하지 않는다.
 * 폰의 운동 세션 칼로리는 총 칼로리라서(기초대사 포함) 합계에 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    /** 끼니를 일부만 적은 날의 합계는 하루 섭취량이 아니므로 세 끼가 다 있어야 수지를 계산한다. */
    private static final List<String> MAIN_MEALS = List.of("breakfast", "lunch", "dinner");

    private final HealthDataQueryService healthData;
    private final ExerciseLogService exerciseLogService;
    private final ActivityCoreRepository activityRepo;
    private final UserBodyRecordRepository bodyRepo;

    public DailySummary summarize(long userId, LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("미래 날짜는 집계할 수 없습니다.");
        }
        boolean dayFinished = date.isBefore(today);
        List<String> notes = new ArrayList<>();

        DateRange day = new DateRange(date, date);
        DailyActivity phone = healthData.dailyActivity(userId, day).stream().findFirst().orElse(null);
        List<ExerciseSession> sessions = healthData.exercises(userId, day);
        DailySummary.Meals meals = meals(healthData.allNutritionOfDay(userId, date));
        List<DailySummary.Ride> rides = rides(userId, date, sessions);
        List<DailySummary.Fitness> fitness = fitness(userId, date, notes);
        DailySummary.Body body = body(userId, date);

        DailySummary.Energy energy = energy(body, phone, meals, rides, fitness, sessions, dayFinished, notes);

        DailySummary.Activity activity = phone == null ? null : new DailySummary.Activity(
                phone.steps(), phone.distanceM(), phone.sleepMinutes(), phone.restingHeartRate());
        return new DailySummary(date, dayFinished, body, energy, activity, meals, rides, fitness, notes);
    }

    // ── 에너지 수지 ───────────────────────────────────

    private static DailySummary.Energy energy(DailySummary.Body body,
                                              DailyActivity phone,
                                              DailySummary.Meals meals,
                                              List<DailySummary.Ride> rides,
                                              List<DailySummary.Fitness> fitness,
                                              List<ExerciseSession> sessions,
                                              boolean dayFinished,
                                              List<String> notes) {
        Integer bmr = body == null ? null : body.bmrKcal();
        Integer intake = meals.recordedMeals().isEmpty() && meals.calories() == 0 ? null : meals.calories();
        Integer active = phone == null || phone.activeKcal() == null ? null : (int) Math.round(phone.activeKcal());

        int extraRide = rides.stream()
                .filter(r -> !r.includedInPhoneActivity())
                .mapToInt(DailySummary.Ride::kcal)
                .sum();
        int extraFitness = sessions.stream().anyMatch(DailySummaryService::isStrength) ? 0
                : (int) Math.round(fitness.stream()
                        .map(DailySummary.Fitness::estimatedKcal)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .sum());

        if (bmr == null) notes.add("기초대사량을 계산할 인바디 기록이 없어 수지를 계산하지 않았습니다.");
        if (active == null) notes.add("폰 활동 칼로리가 없어 소모량을 계산하지 않았습니다.");
        if (!meals.missingMeals().isEmpty()) {
            notes.add("끼니 기록이 빠져 있어 수지를 계산하지 않았습니다.");
        }
        if (!dayFinished) notes.add("하루가 끝나지 않아 소모량과 수지를 계산하지 않았습니다.");

        Integer burned = bmr != null && active != null && dayFinished
                ? bmr + active + extraRide + extraFitness
                : null;
        Integer balance = burned != null && intake != null && meals.missingMeals().isEmpty()
                ? intake - burned
                : null;
        Integer percent = bmr != null && bmr > 0 && intake != null && meals.missingMeals().isEmpty()
                ? (int) Math.round(intake * 100.0 / bmr)
                : null;

        return new DailySummary.Energy(intake, bmr, active, extraRide, extraFitness, burned, balance, percent);
    }

    private static boolean isStrength(ExerciseSession s) {
        String type = s.type().toUpperCase(Locale.ROOT);
        return type.contains("STRENGTH") || type.contains("WEIGHT");
    }

    private static boolean isCycling(ExerciseSession s) {
        String type = s.type().toUpperCase(Locale.ROOT);
        return type.contains("BIK") || type.contains("CYCL");
    }

    // ── 항목별 집계 ───────────────────────────────────

    /** 같은 라이딩이 폰에도 있으면(시간이 겹치는 자전거 세션) 폰 활동 칼로리에 이미 포함된 것으로 본다. */
    private List<DailySummary.Ride> rides(long userId, LocalDate date, List<ExerciseSession> sessions) {
        List<DailySummary.Ride> result = new ArrayList<>();
        for (ActivityCore a : activityRepo.findByUserIdAndDateRange(
                userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
            LocalDateTime start = a.getStartTime();
            LocalDateTime end = a.getEndTime() != null ? a.getEndTime() : start;
            boolean included = sessions.stream().anyMatch(s -> isCycling(s)
                    && s.startTime().isBefore(end) && s.endTime().isAfter(start));
            result.add(new DailySummary.Ride(a.getId(), a.getName(), a.getRideType(), start,
                    a.getTotalDistance(), a.getMovingTime(), a.getTotalAscent(), a.getCalories(), included));
        }
        result.sort((x, y) -> x.startTime().compareTo(y.startTime()));
        return result;
    }

    private List<DailySummary.Fitness> fitness(long userId, LocalDate date, List<String> notes) {
        List<ExerciseHistoryResponse> history;
        try {
            history = exerciseLogService.getHistory(userId, date.toString().substring(0, 7));
        } catch (RuntimeException e) {
            // 달 단위 조회라 그 달의 다른 기록 때문에 실패할 수 있다. 나머지 집계는 살린다
            System.out.println("❌ 하루 집계: 피트니스 조회 실패 " + date + " - " + e.getMessage());
            notes.add("피트니스 기록을 읽지 못해 소모량에서 빠졌습니다.");
            return List.of();
        }
        List<DailySummary.Fitness> result = new ArrayList<>();
        for (ExerciseHistoryResponse s : history) {
            if (!date.toString().equals(s.getSessionDate())) continue;
            List<ExerciseHistoryResponse.ExerciseLogDto> logs = s.getLogs() == null ? List.of() : s.getLogs();
            result.add(new DailySummary.Fitness(s.getId(), s.getIsPT(), s.getTotalVolume(), s.getTotalSets(),
                    s.getEstimatedMinutes(), s.getEstimatedCalories(),
                    logs.stream().map(ExerciseHistoryResponse.ExerciseLogDto::getCategoryName)
                            .filter(Objects::nonNull).distinct().toList(),
                    logs.stream().map(ExerciseHistoryResponse.ExerciseLogDto::getExerciseNameKo)
                            .filter(Objects::nonNull).distinct().toList()));
        }
        return result;
    }

    private static DailySummary.Meals meals(List<NutritionEntry> entries) {
        double calories = 0, carbs = 0, protein = 0, fat = 0;
        for (NutritionEntry e : entries) {
            calories += e.calories() == null ? 0 : e.calories();
            carbs += e.carbsG() == null ? 0 : e.carbsG();
            protein += e.proteinG() == null ? 0 : e.proteinG();
            fat += e.fatG() == null ? 0 : e.fatG();
        }
        List<String> recorded = entries.stream().map(NutritionEntry::mealType).filter(Objects::nonNull).distinct().toList();
        List<String> missing = MAIN_MEALS.stream().filter(m -> !recorded.contains(m)).toList();
        return new DailySummary.Meals((int) Math.round(calories), carbs, protein, fat, recorded, missing);
    }

    /** 그날 이전의 최신 인바디. 기록지에 기초대사량이 없으면(대부분 그렇다) 제지방량으로 계산한다. */
    private DailySummary.Body body(long userId, LocalDate date) {
        UserBodyRecord r = bodyRepo
                .findTopByUserIdAndMeasurementTypeAndRecordDateLessThanEqualOrderByRecordDateDesc(
                        userId, MeasurementType.INBODY, date)
                .orElse(null);
        if (r == null) return null;

        Double fatFreeMass = r.getFatFreeMass();
        if (fatFreeMass == null && r.getWeight() != null && r.getBodyFatMass() != null) {
            fatFreeMass = r.getWeight() - r.getBodyFatMass();
        }
        boolean estimated = r.getBasalMetabolicRate() == null;
        Integer bmr = !estimated ? Integer.valueOf((int) Math.round(r.getBasalMetabolicRate()))
                : fatFreeMass == null ? null
                : Integer.valueOf((int) Math.round(WeightLossAnalysisService.bmrFromFatFreeMass(fatFreeMass)));
        return new DailySummary.Body(r.getRecordDate(), r.getWeight(), fatFreeMass, bmr, estimated);
    }
}

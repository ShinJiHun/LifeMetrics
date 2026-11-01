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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailySummaryServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 31);
    private static final LocalDate NEXT = DAY.plusDays(1);
    private static final DateRange RANGE = new DateRange(DAY, DAY);

    private final HealthDataQueryService health = mock(HealthDataQueryService.class);
    private final ExerciseLogService exerciseLog = mock(ExerciseLogService.class);
    private final ActivityCoreRepository activity = mock(ActivityCoreRepository.class);
    private final UserBodyRecordRepository body = mock(UserBodyRecordRepository.class);
    private final DailySummaryService service = new DailySummaryService(health, exerciseLog, activity, body);

    @BeforeEach
    void setUp() {
        // 제지방량 60kg → 370 + 21.6 × 60 = 1666
        inbody(60.0, null);
        when(health.dailyActivity(1L, RANGE)).thenReturn(List.of(phone(500.0)));
        when(health.allNutritionOfDay(1L, DAY)).thenReturn(threeMeals());
    }

    private void inbody(Double fatFreeMass, Double bmr) {
        UserBodyRecord r = mock(UserBodyRecord.class);
        when(r.getRecordDate()).thenReturn(LocalDate.of(2026, 10, 21));
        when(r.getFatFreeMass()).thenReturn(fatFreeMass);
        when(r.getBasalMetabolicRate()).thenReturn(bmr);
        when(body.findTopByUserIdAndMeasurementTypeAndRecordDateLessThanEqualOrderByRecordDateDesc(
                1L, MeasurementType.INBODY, DAY)).thenReturn(Optional.of(r));
    }

    private static DailyActivity phone(Double active) {
        return new DailyActivity(DAY, 10000L, 7000.0, active, 9999.0, null, null, null);
    }

    private static NutritionEntry meal(String type, double kcal) {
        return new NutritionEntry("id-" + type, DAY, type, type, null, kcal, 10.0, 20.0, 5.0, null, null, null);
    }

    private static List<NutritionEntry> threeMeals() {
        return List.of(meal("breakfast", 500), meal("lunch", 700), meal("dinner", 800));
    }

    private void ride(int kcal) {
        ActivityCore a = mock(ActivityCore.class);
        when(a.getStartTime()).thenReturn(DAY.atTime(7, 0));
        when(a.getEndTime()).thenReturn(DAY.atTime(9, 0));
        when(a.getCalories()).thenReturn(kcal);
        when(activity.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(a));
    }

    private void phoneSession(String type, int startHour, int endHour) {
        when(health.exercises(1L, RANGE)).thenReturn(List.of(new ExerciseSession(
                "s", type, null, DAY.atTime(startHour, 0), DAY.atTime(endHour, 0), 60, 13000.0, null, null, "app")));
    }

    private void weightSession(double kcal) {
        when(exerciseLog.getHistory(1L, "2026-10")).thenReturn(List.of(
                ExerciseHistoryResponse.builder().id(1L).sessionDate(DAY.toString()).estimatedCalories(kcal).build()));
    }

    private DailySummary summarize() {
        return service.summarize(1L, DAY, NEXT);
    }

    @Test
    void 소모는_기초대사량과_활동_칼로리의_합이고_폰_총칼로리는_쓰지_않는다() {
        DailySummary.Energy e = summarize().energy();

        assertThat(e.bmrKcal()).isEqualTo(1666);
        assertThat(e.burnedKcal()).isEqualTo(1666 + 500);
        assertThat(e.intakeKcal()).isEqualTo(2000);
        assertThat(e.balanceKcal()).isEqualTo(2000 - 2166);
        assertThat(e.intakePercentOfBmr()).isEqualTo(120);
    }

    @Test
    void 기록지의_기초대사량이_있으면_계산값보다_우선한다() {
        inbody(60.0, 1700.0);

        DailySummary s = summarize();

        assertThat(s.body().bmrKcal()).isEqualTo(1700);
        assertThat(s.body().bmrEstimated()).isFalse();
    }

    @Test
    void 폰에_겹치는_자전거_세션이_있으면_라이딩_칼로리를_더하지_않는다() {
        ride(1000);
        phoneSession("BIKING", 7, 9);

        DailySummary s = summarize();

        assertThat(s.rides().get(0).includedInPhoneActivity()).isTrue();
        assertThat(s.energy().extraRideKcal()).isZero();
        assertThat(s.energy().burnedKcal()).isEqualTo(2166);
    }

    @Test
    void 폰에_없는_라이딩은_소모에_더한다() {
        ride(1000);
        phoneSession("BIKING", 18, 19); // 다른 시간대의 세션은 같은 라이딩이 아니다

        DailySummary s = summarize();

        assertThat(s.rides().get(0).includedInPhoneActivity()).isFalse();
        assertThat(s.energy().burnedKcal()).isEqualTo(3166);
    }

    @Test
    void 웨이트는_폰에_근력_세션이_없을_때만_더한다() {
        weightSession(300);
        assertThat(summarize().energy().burnedKcal()).isEqualTo(2466);

        phoneSession("STRENGTH_TRAINING", 18, 19);
        assertThat(summarize().energy().burnedKcal()).isEqualTo(2166);
    }

    @Test
    void 끼니가_빠지면_수지를_내지_않고_빠진_끼니를_알려준다() {
        when(health.allNutritionOfDay(1L, DAY)).thenReturn(List.of(meal("breakfast", 500), meal("snack", 200)));

        DailySummary s = summarize();

        assertThat(s.meals().missingMeals()).containsExactly("lunch", "dinner");
        assertThat(s.energy().intakeKcal()).isEqualTo(700);
        assertThat(s.energy().burnedKcal()).isEqualTo(2166);
        assertThat(s.energy().balanceKcal()).isNull();
        assertThat(s.energy().intakePercentOfBmr()).isNull();
        assertThat(s.notes()).anyMatch(n -> n.contains("끼니"));
    }

    @Test
    void 끝나지_않은_날은_소모와_수지를_내지_않는다() {
        DailySummary s = service.summarize(1L, DAY, DAY);

        assertThat(s.dayFinished()).isFalse();
        assertThat(s.energy().phoneActiveKcal()).isEqualTo(500);
        assertThat(s.energy().burnedKcal()).isNull();
        assertThat(s.energy().balanceKcal()).isNull();
    }

    @Test
    void 인바디가_없으면_기초대사량_기준_값을_내지_않는다() {
        when(body.findTopByUserIdAndMeasurementTypeAndRecordDateLessThanEqualOrderByRecordDateDesc(
                1L, MeasurementType.INBODY, DAY)).thenReturn(Optional.empty());

        DailySummary s = summarize();

        assertThat(s.body()).isNull();
        assertThat(s.energy().burnedKcal()).isNull();
        assertThat(s.energy().intakePercentOfBmr()).isNull();
    }

    @Test
    void 활동_기록이_없으면_소모를_내지_않는다() {
        when(health.dailyActivity(1L, RANGE)).thenReturn(List.of());

        DailySummary s = summarize();

        assertThat(s.activity()).isNull();
        assertThat(s.energy().burnedKcal()).isNull();
    }

    @Test
    void 피트니스_조회가_실패해도_나머지는_집계한다() {
        when(exerciseLog.getHistory(any(), anyString())).thenThrow(new RuntimeException("bad data"));

        DailySummary s = summarize();

        assertThat(s.fitness()).isEmpty();
        assertThat(s.energy().burnedKcal()).isEqualTo(2166);
        assertThat(s.notes()).anyMatch(n -> n.contains("피트니스"));
    }

    @Test
    void 다른_날의_피트니스는_세지_않는다() {
        when(exerciseLog.getHistory(1L, "2026-10")).thenReturn(List.of(
                ExerciseHistoryResponse.builder().id(1L).sessionDate("2026-10-30").estimatedCalories(300.0).build()));

        assertThat(summarize().fitness()).isEmpty();
    }

    @Test
    void 미래_날짜는_거부한다() {
        assertThatThrownBy(() -> service.summarize(1L, NEXT, DAY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

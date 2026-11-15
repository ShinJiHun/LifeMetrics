package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.health.repository.HealthTreatmentRepository;
import com.lifemetrics.backend.health.repository.HealthTreatmentVisitRepository;
import com.lifemetrics.backend.health.service.DailySummaryService;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.BikeFitRepository;
import com.lifemetrics.backend.repository.BikeRepository;
import com.lifemetrics.backend.repository.BodyGoalRepository;
import com.lifemetrics.backend.repository.RiderMeasurementRepository;
import com.lifemetrics.backend.repository.RiderZoneSettingRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AthleteDatasetServiceTest {

    private final DailySummaryService daily = mock(DailySummaryService.class);
    private final ActivityCoreRepository activities = mock(ActivityCoreRepository.class);
    private final ExerciseLogService exercises = mock(ExerciseLogService.class);
    private final UserBodyRecordRepository body = mock(UserBodyRecordRepository.class);
    private final BodyGoalRepository goals = mock(BodyGoalRepository.class);
    private final HealthTreatmentRepository treatments = mock(HealthTreatmentRepository.class);
    private final HealthTreatmentVisitRepository visits = mock(HealthTreatmentVisitRepository.class);
    private final RiderMeasurementRepository measurements = mock(RiderMeasurementRepository.class);
    private final BikeRepository bikes = mock(BikeRepository.class);
    private final BikeFitRepository fits = mock(BikeFitRepository.class);
    private final RiderZoneSettingRepository zones = mock(RiderZoneSettingRepository.class);

    private final AthleteContextService context = new AthleteContextService(daily, activities, exercises, body, goals,
            treatments, visits, measurements, bikes, fits, zones,
            mock(com.lifemetrics.backend.repository.RandonneuringResultRepository.class));

    @Test
    void 공개용_컨텍스트는_진료_체성분_식단_신체치수를_아예_조회하지_않는다() {
        String ctx = context.buildPublic(1L, LocalDate.of(2026, 11, 1));

        verifyNoInteractions(daily, body, goals, treatments, visits, measurements, fits);
        assertThat(ctx).contains("랜도너스", "라이딩", "웨이트")
                .doesNotContain("체성분", "진료", "치료", "섭취", "식단", "신체 치수", "피팅");
    }

    @Test
    void 기준일_이후_체성분_기록은_넣지_않는다() {
        UserBodyRecord before = record(LocalDate.of(2026, 10, 1), 80.0);
        UserBodyRecord after = record(LocalDate.of(2026, 11, 5), 77.0);
        when(body.findByUserIdOrderByRecordDate(1L)).thenReturn(List.of(before, after));

        String ctx = context.build(1L, LocalDate.of(2026, 11, 1));

        assertThat(ctx).contains("2026-10-01").contains("체중 80kg").doesNotContain("2026-11-05").doesNotContain("체중 77kg");
    }

    @Test
    void 모드는_public_admin_만() {
        assertThat(AthleteDatasetService.normalizeMode(" Admin ")).isEqualTo("admin");
        assertThat(AthleteDatasetService.normalizeMode("public")).isEqualTo("public");
        assertThatThrownBy(() -> AthleteDatasetService.normalizeMode("all")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AthleteDatasetService.normalizeMode(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 모드별_프롬프트_뒤에_컨텍스트가_붙는다() {
        assertThat(AthleteDatasetService.systemPrompt("public", "CTX")).contains("개인 비서").endsWith("# 공개 기록\nCTX");
        assertThat(AthleteDatasetService.systemPrompt("admin", "CTX")).contains("개인 분석가").endsWith("# 신지훈의 기록\nCTX");
    }

    private static UserBodyRecord record(LocalDate d, double weight) {
        UserBodyRecord r = mock(UserBodyRecord.class);
        when(r.getRecordDate()).thenReturn(d);
        when(r.getMeasurementType()).thenReturn(com.lifemetrics.backend.entity.MeasurementType.INBODY);
        when(r.getWeight()).thenReturn(weight);
        return r;
    }
}

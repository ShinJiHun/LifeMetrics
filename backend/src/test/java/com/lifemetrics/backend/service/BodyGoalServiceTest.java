package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.BodyGoalDto;
import com.lifemetrics.backend.dto.RiderZoneSettingDto;
import com.lifemetrics.backend.entity.BodyGoal;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.repository.BodyGoalRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BodyGoalServiceTest {

    private final BodyGoalRepository goalRepo = mock(BodyGoalRepository.class);
    private final UserBodyRecordRepository recordRepo = mock(UserBodyRecordRepository.class);
    private final RiderZoneSettingService zoneService = mock(RiderZoneSettingService.class);
    private BodyGoalService service;

    @BeforeEach
    void setUp() {
        service = new BodyGoalService(goalRepo, recordRepo, zoneService, mock(ClaudeClient.class));
    }

    private UserBodyRecord inbody(double weight, double fat, double muscle, double bmi) {
        UserBodyRecord r = new UserBodyRecord();
        r.setId(10L);
        r.setRecordDate(LocalDate.of(2026, 9, 1));
        r.setMeasurementType(MeasurementType.INBODY);
        r.setWeight(weight);
        r.setBodyFatMass(fat);
        r.setSkeletalMuscleMass(muscle);
        r.setBmi(bmi);
        return r;
    }

    private void latestInbody(UserBodyRecord r) {
        when(recordRepo.findTopByUserIdAndMeasurementTypeOrderByRecordDateDesc(1L, MeasurementType.INBODY))
                .thenReturn(Optional.of(r));
    }

    @Test
    void 기록지_조절값이_있으면_그대로_목표로_쓴다() {
        UserBodyRecord r = inbody(80.0, 18.0, 36.0, 25.0);
        r.setTargetWeight(72.5);
        r.setFatControl(-8.5);
        r.setMuscleControl(1.0);
        latestInbody(r);

        BodyGoalDto.Suggestion s = service.suggest(1L);

        assertThat(s.source()).isEqualTo("INBODY");
        assertThat(s.targetWeight()).isEqualTo(72.5);
        assertThat(s.targetFatMass()).isEqualTo(9.5);
        assertThat(s.targetMuscleMass()).isEqualTo(37.0);
        // 키 = sqrt(80/25) = 1.789m, 표준체중 = 22 × 3.2 = 70.4
        assertThat(s.heightCm()).isEqualTo(178.9);
        assertThat(s.standardWeight()).isEqualTo(70.4);
        // 감량 7.5kg / (80 × 0.5%) = 18.75주
        assertThat(s.weeksAtHalfPercent()).isEqualTo(18.8);
        assertThat(s.targetDate()).isEqualTo(LocalDate.now().plusWeeks(19));
    }

    @Test
    void 조절값이_없으면_제지방량_유지_체지방률_15퍼센트로_계산한다() {
        latestInbody(inbody(80.0, 20.0, 36.0, 25.0));

        BodyGoalDto.Suggestion s = service.suggest(1L);

        // 제지방 60kg 유지, 체지방 = 60 × 15/85 = 10.6, 목표체중 70.6
        assertThat(s.source()).isEqualTo("FORMULA");
        assertThat(s.targetFatMass()).isEqualTo(10.6);
        assertThat(s.targetWeight()).isEqualTo(70.6);
        assertThat(s.targetMuscleMass()).isEqualTo(36.0);
    }

    @Test
    void 이미_표준_체지방률_이하면_체지방을_늘리라고_하지_않는다() {
        latestInbody(inbody(70.0, 7.0, 36.0, 22.0));

        BodyGoalDto.Suggestion s = service.suggest(1L);

        assertThat(s.targetFatMass()).isEqualTo(7.0);
        assertThat(s.targetWeight()).isEqualTo(70.0);
        assertThat(s.targetDate()).isNull();
    }

    @Test
    void 진행률과_WKG를_계산한다() {
        BodyGoal goal = new BodyGoal();
        goal.setStartDate(LocalDate.now().minusDays(30));
        goal.setStartWeight(80.0);
        goal.setStartFatMass(18.0);
        goal.setStartMuscleMass(36.0);
        goal.setTargetWeight(72.0);
        goal.setTargetFatMass(10.0);
        goal.setTargetMuscleMass(37.0);
        goal.setSource("INBODY");
        when(goalRepo.findTopByUserIdAndStatusOrderByIdDesc(1L, BodyGoal.ACTIVE)).thenReturn(Optional.of(goal));

        UserBodyRecord scale = new UserBodyRecord();
        scale.setRecordDate(LocalDate.now());
        scale.setWeight(78.0);
        when(recordRepo.findByUserIdOrderByRecordDate(1L)).thenReturn(List.of(inbody(79.0, 16.0, 36.0, 24.7), scale));
        latestInbody(inbody(79.0, 16.0, 36.0, 24.7));
        when(zoneService.getEffective(any())).thenReturn(new RiderZoneSettingDto(240, 190, false, true));

        BodyGoalDto.Status s = service.status(1L).orElseThrow();

        assertThat(s.current().weight()).isEqualTo(78.0);   // 체중계 최신값
        assertThat(s.current().fatMass()).isEqualTo(16.0);  // 인바디 최신값
        assertThat(s.progress().weightPct()).isEqualTo(25.0);
        assertThat(s.progress().fatPct()).isEqualTo(25.0);
        assertThat(s.progress().musclePct()).isEqualTo(0.0);
        assertThat(s.progress().daysElapsed()).isEqualTo(30L);
        assertThat(s.wkgStart()).isEqualTo(3.0);
        assertThat(s.wkgTarget()).isEqualTo(3.33);
    }
}

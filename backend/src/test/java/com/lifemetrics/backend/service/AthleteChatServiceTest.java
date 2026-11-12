package com.lifemetrics.backend.service;

import com.lifemetrics.backend.client.ClaudeChatClient;
import com.lifemetrics.backend.dto.PersonaChatRequest;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.Bike;
import com.lifemetrics.backend.entity.BikeFit;
import com.lifemetrics.backend.entity.RiderMeasurement;
import com.lifemetrics.backend.health.dto.DailySummary;
import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.entity.HealthTreatmentVisit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AthleteChatServiceTest {

    private static PersonaChatRequest.Message msg(String role, String content) {
        PersonaChatRequest.Message m = new PersonaChatRequest.Message();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    void 대화는_user_로_시작하고_같은_역할이_이어지면_합친다() {
        var out = AthleteChatService.normalize(List.of(
                msg("assistant", "안녕하세요, 코치입니다"),
                msg("user", "감량 전략"),
                msg("user", "짜줘"),
                msg("assistant", " "),
                msg("assistant", "좋아요")));

        assertThat(out).containsExactly(
                new ClaudeChatClient.Message("user", "감량 전략\n\n짜줘"),
                new ClaudeChatClient.Message("assistant", "좋아요"));
    }

    @Test
    void 최근_20개만_보낸다() {
        List<PersonaChatRequest.Message> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) many.add(msg(i % 2 == 0 ? "user" : "assistant", "m" + i));

        var out = AthleteChatService.normalize(many);

        assertThat(out).hasSize(AthleteChatService.MAX_HISTORY);
        assertThat(out.get(0).content()).isEqualTo("m10");
    }

    @Test
    void 빈_대화는_빈_목록() {
        assertThat(AthleteChatService.normalize(null)).isEmpty();
        assertThat(AthleteChatService.normalize(List.of(msg("assistant", "hi")))).isEmpty();
    }

    @Test
    void 시스템_프롬프트에_기록이_들어간다() {
        String p = AthleteChatService.systemPrompt("## 체성분\n- 84.5kg\n");
        assertThat(p).contains("애슐리트 페르소나").contains("이상치로 취급하지 마세요").endsWith("- 84.5kg\n");
    }

    @Test
    void 일별_요약_한_줄() {
        var s = new DailySummary(
                LocalDate.of(2026, 11, 6), true, null,
                new DailySummary.Energy(1843, 1800, 500, 0, 0, 2400, -557, 102),
                new DailySummary.Activity(8000L, null, null, null),
                new DailySummary.Meals(1843, 200.4, 110.6, 60.2, List.of("breakfast", "lunch"), List.of("dinner")),
                List.of(new DailySummary.Ride(1L, "출근", "GENERAL", LocalDateTime.of(2026, 11, 6, 8, 0),
                        25300.0, 3600, 120.0, 600, false)),
                List.of(), List.of());

        assertThat(AthleteContextService.summaryLine(s)).isEqualTo(
                "2026-11-06: 섭취 1843kcal (탄200/단111/지60g, 기록 끼니 breakfast·lunch, 빠진 끼니 dinner)"
                        + ", 소모 2400kcal, 수지 -557kcal, 걸음 8000, 라이딩 25.3km");
    }

    @Test
    void 식단이_없는_오늘() {
        var s = new DailySummary(LocalDate.of(2026, 11, 7), false, null, null, null,
                new DailySummary.Meals(0, 0, 0, 0, List.of(), List.of("breakfast", "lunch", "dinner")),
                List.of(), List.of(), List.of());
        assertThat(AthleteContextService.summaryLine(s)).isEqualTo("2026-11-07(오늘, 진행 중): 식단 기록 없음");
    }

    @Test
    void 라이딩_한_줄() {
        ActivityCore a = new ActivityCore();
        ReflectionTestUtils.setField(a, "startTime", LocalDateTime.of(2026, 10, 3, 5, 0));
        ReflectionTestUtils.setField(a, "rideType", "BREVET");
        ReflectionTestUtils.setField(a, "name", "SBS 1000");
        ReflectionTestUtils.setField(a, "totalDistance", 400_123.0);
        ReflectionTestUtils.setField(a, "movingTime", 71_100);
        ReflectionTestUtils.setField(a, "totalAscent", 3210.4);
        ReflectionTestUtils.setField(a, "calories", 13296);

        assertThat(AthleteContextService.rideLine(a))
                .isEqualTo("2026-10-03 [BREVET] SBS 1000: 400.1km, 이동 19시간 45분, 상승 3210m, 13296kcal");
    }

    @Test
    void 숫자_형식() {
        assertThat(AthleteContextService.num(84.0, "kg")).isEqualTo("84kg");
        assertThat(AthleteContextService.num(15.27, "%")).isEqualTo("15.3%");
        assertThat(AthleteContextService.num(null, "kg")).isEqualTo("-");
        assertThat(AthleteContextService.signed(300)).isEqualTo("+300");
    }

    @Test
    void 치료_한_줄() {
        HealthTreatment t = new HealthTreatment();
        t.setName("장경인대 증후군");
        t.setDiagnosisCode("M76.3");
        t.setStartDate(LocalDate.of(2026, 10, 1));
        t.setRestrictions("고강도 인터벌 2주 금지");
        assertThat(AthleteContextService.treatmentLine(t, LocalDate.of(2026, 11, 12)))
                .isEqualTo("장경인대 증후군 [M76.3]: 2026-10-01 시작, 치료 중 42일째 / 운동 제한·권고: 고강도 인터벌 2주 금지");
    }

    @Test
    void 진료_한_줄은_진찰을_빼고_복용_중인_약을_표시한다() {
        HealthTreatmentVisit v = new HealthTreatmentVisit();
        v.setVisitDate(LocalDate.of(2026, 11, 10));
        v.setInstitution("튼튼정형외과의원");
        v.setDocType("진료비 세부내역서");
        v.setPatientAmount(12300);
        v.setItems(List.of(
                Map.of("category", "진찰", "name", "재진진찰료"),
                Map.of("category", "물리치료", "name", "체외충격파"),
                Map.of("category", "약", "name", "록소프로펜정", "dose", "1정", "timesPerDay", 3, "days", 5,
                        "drugClass", "소염진통제(NSAID)")));
        assertThat(AthleteContextService.visitLine(v, "장경인대 증후군", LocalDate.of(2026, 11, 12)))
                .isEqualTo("2026-11-10 튼튼정형외과의원 [진료비 세부내역서] (장경인대 증후군 치료): 물리치료: 체외충격파; "
                        + "약: 록소프로펜정 1정×3회 5일분 (소염진통제(NSAID)) — 오늘 복용 기간 중 / 본인부담 12,300원");
    }

    @Test
    void 신체_치수_한_줄은_핵심_컬럼과_상세_항목을_함께_쓴다() {
        RiderMeasurement m = new RiderMeasurement();
        m.setMeasureDate(LocalDate.of(2026, 11, 9));
        m.setFootLengthLeftMm(252.0);
        m.setFootLengthRightMm(256.0);
        m.setDetailJson(Map.of("unit", "mm", "items", List.of(
                Map.of("item", "Arch height", "left", 12, "right", 14),
                Map.of("item", "Calf", "value", 380))));
        assertThat(AthleteContextService.measurementLine(m)).isEqualTo(
                "2026-11-09: 발길이 L252/R256 / 상세(mm): Arch height L12/R14, Calf 380");
    }

    @Test
    void 자전거와_피팅_한_줄() {
        Bike b = new Bike();
        b.setName("GTR PRO TE");
        b.setIsDefault(true);
        b.setFrameSize("S");
        b.setWeightKg(7.5);
        b.setChainringOuter(50);
        b.setChainringInner(34);
        b.setCassetteLabel("11-34T");
        b.setTotalDistance(2_017_199.0);
        assertThat(AthleteContextService.bikeLine(b))
                .isEqualTo("GTR PRO TE (주력): 프레임 S, 7.5kg, 50/34T × 11-34T, 누적 2,017km");

        BikeFit f = new BikeFit();
        f.setFitDate(LocalDate.of(2026, 7, 1));
        f.setSaddleHeightMm(705.0);
        f.setSaddleTiltDeg(-1.5);
        f.setCrankLengthMm(165.0);
        f.setExtraJson(Map.of("pedal_stack_mm", 11));
        assertThat(AthleteContextService.fitLine(f))
                .isEqualTo("피팅 2026-07-01: 안장높이 705, 안장각 -1.5°, 크랭크 165, pedal_stack_mm 11");
    }
}

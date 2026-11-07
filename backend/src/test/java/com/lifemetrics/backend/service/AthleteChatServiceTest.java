package com.lifemetrics.backend.service;

import com.lifemetrics.backend.client.ClaudeChatClient;
import com.lifemetrics.backend.dto.PersonaChatRequest;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.health.dto.DailySummary;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
}

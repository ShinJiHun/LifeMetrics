package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.service.AthleteContextService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AthleteToolsTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    private static JsonNode in(String json) throws Exception {
        return OM.readTree(json);
    }

    private static ActivityCore ride(String date, String type, double km) {
        ActivityCore a = new ActivityCore();
        ReflectionTestUtils.setField(a, "startTime", LocalDate.parse(date).atTime(7, 0));
        ReflectionTestUtils.setField(a, "rideType", type);
        ReflectionTestUtils.setField(a, "name", "ride " + date);
        ReflectionTestUtils.setField(a, "totalDistance", km * 1000);
        return a;
    }

    @Test
    void get_activities_는_기간을_포함_범위로_조회하고_유형으로_거른다() throws Exception {
        ActivityCoreRepository repo = mock(ActivityCoreRepository.class);
        when(repo.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(ride("2026-08-20", "BREVET", 200), ride("2026-08-03", null, 42.5)));

        String out = new GetActivitiesTool(repo).execute(
                in("{\"from\":\"2026-08-01\",\"to\":\"2026-08-31\",\"ride_type\":\"brevet\"}"), 1L, TODAY);

        verify(repo).findByUserIdAndDateRange(1L, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(out).startsWith("라이딩 2026-08-01 ~ 2026-08-31 [brevet]: 전체 1건")
                .contains("2026-08-20 [BREVET] ride 2026-08-20: 200.0km")
                .doesNotContain("42.5km");
    }

    @Test
    void get_activities_는_limit_을_넘으면_최신_N건만_보여준다() throws Exception {
        ActivityCoreRepository repo = mock(ActivityCoreRepository.class);
        List<ActivityCore> many = new ArrayList<>();
        for (int d = 30; d >= 1; d--) many.add(ride("2026-09-%02d".formatted(d), null, 30));
        when(repo.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(many);

        String out = new GetActivitiesTool(repo).execute(in("{\"from\":\"2026-09-01\",\"limit\":5}"), 1L, TODAY);

        assertThat(out).contains("전체 30건, 최신 5건 표시").contains("2026-09-30").doesNotContain("2026-09-25");
    }

    @Test
    void 날짜_형식과_순서가_틀리면_입력_오류() {
        ActivityCoreRepository repo = mock(ActivityCoreRepository.class);
        GetActivitiesTool tool = new GetActivitiesTool(repo);
        assertThatThrownBy(() -> tool.execute(in("{}"), 1L, TODAY)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from 는 필수");
        assertThatThrownBy(() -> tool.execute(in("{\"from\":\"8월\"}"), 1L, TODAY))
                .hasMessageContaining("형식이 잘못");
        assertThatThrownBy(() -> tool.execute(in("{\"from\":\"2026-09-01\",\"to\":\"2026-08-01\"}"), 1L, TODAY))
                .hasMessageContaining("보다 늦습니다");
    }

    @Test
    void get_daily_summaries_는_31일을_넘으면_거절하고_미래는_오늘까지() throws Exception {
        AthleteContextService ctx = mock(AthleteContextService.class);
        GetDailySummariesTool tool = new GetDailySummariesTool(ctx);

        assertThatThrownBy(() -> tool.execute(in("{\"from\":\"2026-01-01\",\"to\":\"2026-03-01\"}"), 1L, TODAY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("최대 31일");
        verify(ctx, never()).dailySummaryLines(any(Long.class), any(), any(), any());

        when(ctx.dailySummaryLines(1L, LocalDate.of(2026, 11, 10), TODAY, TODAY)).thenReturn(List.of("2026-11-10: 식단 기록 없음"));
        String out = tool.execute(in("{\"from\":\"2026-11-10\",\"to\":\"2026-11-30\"}"), 1L, TODAY);
        assertThat(out).startsWith("일별 요약 2026-11-10 ~ 2026-11-14 (5일)").contains("- 2026-11-10: 식단 기록 없음");
    }

    @Test
    void get_body_records_는_잘못된_type_을_알려준다() {
        GetBodyRecordsTool tool = new GetBodyRecordsTool(mock(AthleteContextService.class));
        assertThatThrownBy(() -> tool.execute(in("{\"type\":\"SCALE\"}"), 1L, TODAY))
                .hasMessageContaining("INBODY 또는 FITDAYS");
    }

    @Test
    void 결과가_길면_잘라서_안내를_붙인다() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 500; i++) lines.add("line " + i + " " + "x".repeat(40));
        String out = ToolInputs.lines("헤더", lines, "기간을 좁혀라");
        assertThat(out.length()).isLessThanOrEqualTo(AthleteTool.MAX_RESULT_CHARS + 100);
        assertThat(out).contains("500건 중").endsWith("기간을 좁혀라)");
        assertThat(ToolInputs.lines("헤더", List.of(), "x")).isEqualTo("헤더\n(기록 없음)");
    }

    @Test
    void 도구_이름은_중복될_수_없다() {
        AthleteContextService ctx = mock(AthleteContextService.class);
        assertThatThrownBy(() -> new AthleteToolRegistry(List.of(new GetBodyRecordsTool(ctx), new GetBodyRecordsTool(ctx))))
                .isInstanceOf(IllegalStateException.class);
    }
}

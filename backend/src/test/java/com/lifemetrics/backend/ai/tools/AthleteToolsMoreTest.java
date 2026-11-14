package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.Segment;
import com.lifemetrics.backend.entity.SegmentEffort;
import com.lifemetrics.backend.health.entity.HealthCheckup;
import com.lifemetrics.backend.health.repository.HealthCheckupRepository;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.ActivityWeatherRepository;
import com.lifemetrics.backend.repository.SegmentEffortRepository;
import com.lifemetrics.backend.repository.SegmentRepository;
import com.lifemetrics.backend.service.AthleteContextService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AthleteToolsMoreTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    private static JsonNode in(String json) throws Exception {
        return OM.readTree(json);
    }

    private static <T> T set(T o, Object... kv) {
        for (int i = 0; i < kv.length; i += 2) ReflectionTestUtils.setField(o, (String) kv[i], kv[i + 1]);
        return o;
    }

    private static ActivityCore ride(long id, String date, double km, int movingSec, Double power) {
        return set(new ActivityCore(), "id", id, "userId", 1L, "startTime", LocalDate.parse(date).atTime(7, 0),
                "totalDistance", km * 1000, "movingTime", movingSec, "totalAscent", 100.0, "avgPower", power,
                "name", "ride " + id);
    }

    @Test
    void get_ride_stats_는_월별로_묶고_파워는_파워_있는_기록만_이동시간_가중평균() throws Exception {
        ActivityCoreRepository repo = mock(ActivityCoreRepository.class);
        when(repo.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(
                ride(1, "2026-08-02", 50, 3600, 200.0),
                ride(2, "2026-08-20", 100, 7200, 140.0),
                ride(3, "2026-08-25", 30, 3600, null),
                ride(4, "2026-10-01", 40, 3600, null)));

        String out = new GetRideStatsTool(repo).execute(in("{\"from\":\"2026-08-01\",\"to\":\"2026-10-31\"}"), 1L, TODAY);

        // 8월: 3회 180km, 이동 4시간, 파워 (200*1h + 140*2h) / 3h = 160W, 최장 100km
        assertThat(out).contains("- 2026-08: 3회, 180.0km, 이동 4시간 0분, 상승 300m, 평균파워 160W, 최장 100.0km")
                .contains("- 2026-10: 1회, 40.0km").doesNotContain("2026-09");
    }

    @Test
    void get_ride_stats_주별은_월요일_기준() {
        assertThat(GetRideStatsTool.key(LocalDate.of(2026, 11, 14), "week")).isEqualTo("2026-11-09 주");
        assertThatThrownBy(() -> new GetRideStatsTool(mock(ActivityCoreRepository.class))
                .execute(in("{\"from\":\"2026-01-01\",\"group_by\":\"day\"}"), 1L, TODAY))
                .hasMessageContaining("week·month·total");
    }

    @Test
    void get_activity_detail_은_다른_사용자의_기록을_돌려주지_않는다() throws Exception {
        ActivityCoreRepository repo = mock(ActivityCoreRepository.class);
        ActivityCore other = set(ride(9, "2026-08-01", 10, 600, null), "userId", 2L);
        when(repo.findById(9L)).thenReturn(Optional.of(other));
        GetActivityDetailTool tool = new GetActivityDetailTool(repo, mock(ActivityWeatherRepository.class),
                mock(SegmentEffortRepository.class), mock(SegmentRepository.class));

        assertThatThrownBy(() -> tool.execute(in("{\"activity_id\":9}"), 1L, TODAY)).hasMessageContaining("id 9 인 라이딩 기록이 없습니다");
    }

    @Test
    void get_activity_detail_은_측정값과_구간을_보여준다() throws Exception {
        ActivityCoreRepository repo = mock(ActivityCoreRepository.class);
        when(repo.findById(5L)).thenReturn(Optional.of(set(ride(5, "2026-08-01", 60, 7200, 180.0),
                "avgSpeed", 28.5, "maxSpeed", 55.0, "normalizedPower", 195.0)));
        SegmentEffortRepository efforts = mock(SegmentEffortRepository.class);
        when(efforts.findByActivityCoreId(5L)).thenReturn(List.of(set(new SegmentEffort(),
                "segmentId", 7L, "elapsedTimeSec", 754, "avgPower", 250.0, "prRank", 2)));
        SegmentRepository segs = mock(SegmentRepository.class);
        when(segs.findById(7L)).thenReturn(Optional.of(set(new Segment(), "id", 7L, "name", "남산 업힐")));
        ActivityWeatherRepository weather = mock(ActivityWeatherRepository.class);
        when(weather.findByActivityCoreId(anyLong())).thenReturn(Optional.empty());

        String out = new GetActivityDetailTool(repo, weather, efforts, segs).execute(in("{\"activity_id\":5}"), 1L, TODAY);

        assertThat(out).contains("속도 평균 28.5km/h / 최대 55km/h").contains("NP 195W")
                .contains("구간 [7] 남산 업힐: 12분 34초, 평균파워 250W, PR 2위");
    }

    @Test
    void get_segment_history_는_이름이_여러_개면_후보를_돌려준다() {
        SegmentRepository segs = mock(SegmentRepository.class);
        when(segs.findByIsActiveTrue()).thenReturn(List.of(
                set(new Segment(), "id", 1L, "name", "남산 업힐 북측"),
                set(new Segment(), "id", 2L, "name", "남산 업힐 남측"),
                set(new Segment(), "id", 3L, "name", "북악")));
        GetSegmentHistoryTool tool = new GetSegmentHistoryTool(segs, mock(SegmentEffortRepository.class));

        assertThatThrownBy(() -> tool.execute(in("{\"name\":\"남산업힐\"}"), 1L, TODAY))
                .hasMessageContaining("2개").hasMessageContaining("[1] 남산 업힐 북측").hasMessageContaining("[2]");
        assertThatThrownBy(() -> tool.execute(in("{}"), 1L, TODAY)).hasMessageContaining("segment_id 또는 name");
    }

    @Test
    void get_treatments_는_status_를_검사하고_active_면_진행중만_조회한다() throws Exception {
        AthleteContextService ctx = mock(AthleteContextService.class);
        when(ctx.treatmentLines(1L, true, TODAY)).thenReturn(List.of("장경인대 증후군: 치료 중"));
        GetTreatmentsTool tool = new GetTreatmentsTool(ctx);

        assertThat(tool.execute(in("{\"status\":\"active\"}"), 1L, TODAY))
                .isEqualTo("치료·부상 (active)\n- 장경인대 증후군: 치료 중");
        assertThatThrownBy(() -> tool.execute(in("{\"status\":\"done\"}"), 1L, TODAY)).hasMessageContaining("active 또는 all");
    }

    @Test
    void get_health_checkups_는_연도로_거르고_판정을_괄호로_붙인다() throws Exception {
        HealthCheckupRepository repo = mock(HealthCheckupRepository.class);
        when(repo.findByUserIdOrderByCheckupDateDesc(1L)).thenReturn(List.of(
                set(new HealthCheckup(), "checkupDate", LocalDate.of(2026, 5, 1), "overallJudgment", "정상B(경계)",
                        "systolicBp", 128, "diastolicBp", 82, "bpResult", "고혈압 전단계"),
                set(new HealthCheckup(), "checkupDate", LocalDate.of(2024, 5, 1), "overallJudgment", "정상A")));

        String out = new GetHealthCheckupsTool(repo).execute(in("{\"year\":2026}"), 1L, TODAY);

        assertThat(out).startsWith("건강검진 2026년: 1회")
                .contains("종합판정 정상B(경계)").contains("혈압 128/82 (고혈압 전단계)").doesNotContain("정상A");
    }
}

package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.health.dto.HealthDataDto.DailyActivity;
import com.lifemetrics.backend.health.service.HealthDataQueryService;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.AiAnalysisRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DailyAnalysisServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 31);
    private static final String RESULT = "{\"summary\":\"좋은 하루\"}";

    private final AiAnalysisRepository analysisRepo = mock(AiAnalysisRepository.class);
    private final AiAnalysisService ai = mock(AiAnalysisService.class);
    private final HealthDataQueryService health = mock(HealthDataQueryService.class);
    private final ExerciseLogService exerciseLog = mock(ExerciseLogService.class);
    private final ActivityCoreRepository activity = mock(ActivityCoreRepository.class);
    private final UserBodyRecordRepository body = mock(UserBodyRecordRepository.class);
    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    private DailyAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new DailyAnalysisService(analysisRepo, ai, health, exerciseLog, activity, body, om);
        when(analysisRepo.findByUserIdAndAnalysisTypeAndTargetIdAndTargetPeriod(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ai.requestClaude(anyString())).thenReturn(RESULT);
    }

    private void steps(LocalDate date, long steps) {
        when(health.dailyActivity(eq(1L), eq(new HealthDataQueryService.DateRange(date, date))))
                .thenReturn(List.of(new DailyActivity(date, steps, null, null, null, null, null, null)));
    }

    private AiAnalysis savedAfterFirstRun() {
        service.analyzeIfChanged(1L, DAY);
        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisRepo).save(captor.capture());
        when(analysisRepo.findByUserIdAndAnalysisTypeAndTargetIdAndTargetPeriod(1L, "daily", 0L, DAY.toString()))
                .thenReturn(Optional.of(captor.getValue()));
        return captor.getValue();
    }

    @Test
    void 기록이_없는_날은_AI_를_호출하지_않는다() {
        assertThat(service.analyzeIfChanged(1L, DAY)).isFalse();
        verifyNoInteractions(ai);
        verify(analysisRepo, never()).save(any());
    }

    @Test
    void 분석을_날짜와_입력_결과와_함께_저장한다() throws Exception {
        steps(DAY, 1000);

        var saved = savedAfterFirstRun();

        assertThat(saved.getAnalysisType()).isEqualTo("daily");
        assertThat(saved.getTargetId()).isZero();
        assertThat(saved.getTargetPeriod()).isEqualTo("2026-10-31");
        var data = om.readTree(saved.getAnalysisData());
        assertThat(data.path("result").path("summary").asText()).isEqualTo("좋은 하루");
        assertThat(data.path("input").path("dailyActivity").get(0).path("steps").asLong()).isEqualTo(1000);
    }

    @Test
    void 입력이_그대로면_다시_호출하지_않는다() {
        steps(DAY, 1000);
        savedAfterFirstRun();

        assertThat(service.analyzeIfChanged(1L, DAY)).isFalse();
        verify(ai, times(1)).requestClaude(anyString());
    }

    @Test
    void 동기화가_늦게_들어와_입력이_바뀌면_덮어쓴다() {
        steps(DAY, 1000);
        var saved = savedAfterFirstRun();
        String before = saved.getAnalysisData();

        steps(DAY, 9000);
        assertThat(service.analyzeIfChanged(1L, DAY)).isTrue();

        assertThat(saved.getAnalysisData()).isNotEqualTo(before).contains("9000");
        verify(ai, times(2)).requestClaude(anyString());
    }

    @Test
    void 호출이_실패하면_기존_분석을_덮어쓰지_않는다() {
        steps(DAY, 1000);
        var saved = savedAfterFirstRun();
        String before = saved.getAnalysisData();

        steps(DAY, 9000);
        when(ai.requestClaude(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.analyzeIfChanged(1L, DAY))
                .isInstanceOf(DailyAnalysisService.AnalysisFailedException.class);
        assertThat(saved.getAnalysisData()).isEqualTo(before);
        verify(analysisRepo, times(1)).save(any());
    }

    @Test
    void 재분석은_입력이_그대로여도_다시_호출한다() {
        steps(DAY, 1000);
        savedAfterFirstRun();

        assertThat(service.analyzeNow(1L, DAY)).isPresent();
        verify(ai, times(2)).requestClaude(anyString());
    }

    @Test
    void 새벽_배치는_오늘을_빼고_어제부터_3일을_살핀다() {
        LocalDate today = LocalDate.of(2026, 11, 2);
        steps(today, 1);
        steps(today.minusDays(1), 2);
        steps(today.minusDays(3), 3);
        steps(today.minusDays(4), 4);

        assertThat(service.runNightly(1L, today)).isEqualTo(2);

        verify(health, never()).dailyActivity(eq(1L), eq(new HealthDataQueryService.DateRange(today, today)));
        verify(health, never()).dailyActivity(eq(1L),
                eq(new HealthDataQueryService.DateRange(today.minusDays(4), today.minusDays(4))));
    }

    @Test
    void 새벽_배치는_한_날짜가_실패해도_나머지를_진행한다() {
        LocalDate today = LocalDate.of(2026, 11, 2);
        steps(today.minusDays(1), 1);
        steps(today.minusDays(2), 2);
        when(ai.requestClaude(anyString())).thenReturn(null).thenReturn(RESULT);

        assertThat(service.runNightly(1L, today)).isEqualTo(1);
    }

    @Test
    void 프롬프트는_그_날짜만_근거로_하라고_지시한다() {
        String prompt = DailyAnalysisService.buildPrompt(DAY, "{\"x\":1}");
        assertThat(prompt).contains("2026-10-31").contains("이 날의 기록만").contains("{\"x\":1}");
    }
}

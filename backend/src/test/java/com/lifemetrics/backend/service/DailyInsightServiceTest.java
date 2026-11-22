package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.entity.AthleteChatMessage;
import com.lifemetrics.backend.persona.diary.DiaryService;
import com.lifemetrics.backend.persona.diary.LectureService;
import com.lifemetrics.backend.repository.AiAnalysisRepository;
import com.lifemetrics.backend.repository.AthleteChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DailyInsightServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);
    private static final String RESULT = "{\"title\":\"바쁜 하루\",\"connections\":[]}";

    private final AiAnalysisRepository analysisRepo = mock(AiAnalysisRepository.class);
    private final AiAnalysisService ai = mock(AiAnalysisService.class);
    private final DailyAnalysisService daily = mock(DailyAnalysisService.class);
    private final DiaryService diary = mock(DiaryService.class);
    private final LectureService lectures = mock(LectureService.class);
    private final AthleteChatMessageRepository chat = mock(AthleteChatMessageRepository.class);
    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    private DailyInsightService service;

    @BeforeEach
    void setUp() {
        service = new DailyInsightService(analysisRepo, ai, daily, diary, lectures, chat, om);
        when(analysisRepo.findByUserIdAndAnalysisTypeAndTargetIdAndTargetPeriod(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ai.requestClaude(anyString())).thenReturn(RESULT);
        when(daily.collectInput(anyLong(), any())).thenReturn(new LinkedHashMap<>(Map.of("rides", List.of(), "meals", List.of())));
        when(daily.find(anyLong(), any())).thenReturn(Optional.empty());
        when(diary.notes(any())).thenReturn(List.of());
        when(diary.diaryOf(any())).thenReturn(Optional.empty());
        when(diary.workLogOf(any())).thenReturn(Map.of());
        when(lectures.forDate(any())).thenReturn(List.of());
        when(chat.findByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void 기록이_하나도_없으면_호출하지_않는다() {
        assertThat(service.analyzeNow(1L, DAY)).isEmpty();
        verifyNoInteractions(ai);
        verify(analysisRepo, never()).save(any());
    }

    @Test
    void 영역별_기록을_모아_분석하고_빈_영역은_뺀다() {
        when(diary.workLogOf(DAY)).thenReturn(Map.of("today_done", "배포"));
        AthleteChatMessage q = new AthleteChatMessage();
        q.setContent("오늘 FTP 테스트 해도 될까?");
        when(chat.findByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(
                "user", DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay())).thenReturn(List.of(q));

        JsonNode saved = service.analyzeNow(1L, DAY).orElseThrow();

        assertThat(saved.path("result").path("title").asText()).isEqualTo("바쁜 하루");
        assertThat(saved.path("sources").toString()).contains("workLog", "athleteChatQuestions").doesNotContain("athleteRecords", "diary");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).requestClaude(prompt.capture());
        assertThat(prompt.getValue()).contains("FTP 테스트", "배포", "2026-11-21");

        ArgumentCaptor<AiAnalysis> entity = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisRepo).save(entity.capture());
        assertThat(entity.getValue().getAnalysisType()).isEqualTo("insight");
        assertThat(entity.getValue().getTargetPeriod()).isEqualTo("2026-11-21");
    }

    @Test
    void 한_영역_조회가_실패해도_나머지로_분석한다() {
        when(diary.notes(any())).thenThrow(new RuntimeException("journal DB 없음"));
        when(diary.workLogOf(DAY)).thenReturn(Map.of("issues", "장애 대응"));

        assertThat(service.analyzeNow(1L, DAY)).isPresent();
    }

    @Test
    void 분석_뒤_기록이_바뀌면_stale() {
        when(diary.workLogOf(DAY)).thenReturn(Map.of("today_done", "배포"));
        service.analyzeNow(1L, DAY);
        ArgumentCaptor<AiAnalysis> entity = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisRepo).save(entity.capture());
        when(analysisRepo.findByUserIdAndAnalysisTypeAndTargetIdAndTargetPeriod(any(), any(), any(), any()))
                .thenReturn(Optional.of(entity.getValue()));

        assertThat(service.find(1L, DAY).orElseThrow().stale()).isFalse();

        when(diary.workLogOf(DAY)).thenReturn(Map.of("today_done", "배포", "issues", "롤백"));
        assertThat(service.find(1L, DAY).orElseThrow().stale()).isTrue();
    }

    @Test
    void AI_호출이_실패하면_저장하지_않는다() {
        when(diary.workLogOf(DAY)).thenReturn(Map.of("today_done", "배포"));
        when(ai.requestClaude(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.analyzeNow(1L, DAY))
                .isInstanceOf(DailyAnalysisService.AnalysisFailedException.class);
        verify(analysisRepo, never()).save(any());
    }
}

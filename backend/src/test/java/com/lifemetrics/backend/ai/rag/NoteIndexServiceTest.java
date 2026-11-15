package com.lifemetrics.backend.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.entity.NoteChunk;
import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.repository.HealthTreatmentRepository;
import com.lifemetrics.backend.health.repository.HealthTreatmentVisitRepository;
import com.lifemetrics.backend.repository.AiAnalysisRepository;
import com.lifemetrics.backend.repository.BodyGoalRepository;
import com.lifemetrics.backend.repository.NoteChunkRepository;
import com.lifemetrics.backend.repository.RandonneuringResultRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteIndexServiceTest {

    private final NoteChunkRepository chunks = mock(NoteChunkRepository.class);
    private final GeminiEmbeddingClient embedder = mock(GeminiEmbeddingClient.class);
    private final HealthTreatmentRepository treatments = mock(HealthTreatmentRepository.class);
    private final HealthTreatmentVisitRepository visits = mock(HealthTreatmentVisitRepository.class);
    private final AiAnalysisRepository analyses = mock(AiAnalysisRepository.class);
    private final BodyGoalRepository goals = mock(BodyGoalRepository.class);
    private final RandonneuringResultRepository rando = mock(RandonneuringResultRepository.class);
    private final NoteIndexService service = new NoteIndexService(chunks, embedder, treatments, visits, analyses, goals, rando, new ObjectMapper());

    {
        when(embedder.model()).thenReturn("gemini-embedding-001");
        when(embedder.dims()).thenReturn(3);
    }

    private static HealthTreatment treatment(long id, String notes) {
        HealthTreatment t = new HealthTreatment();
        ReflectionTestUtils.setField(t, "id", id);
        ReflectionTestUtils.setField(t, "name", "장경인대 증후군");
        ReflectionTestUtils.setField(t, "bodyPart", "오른쪽 무릎 바깥쪽");
        ReflectionTestUtils.setField(t, "startDate", LocalDate.of(2026, 10, 5));
        ReflectionTestUtils.setField(t, "notes", notes);
        return t;
    }

    private static AiAnalysis analysis(long id, String json) {
        AiAnalysis a = new AiAnalysis();
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "analysisType", "exercise_session");
        ReflectionTestUtils.setField(a, "analysisData", json);
        ReflectionTestUtils.setField(a, "createdAt", LocalDateTime.of(2026, 10, 27, 21, 0));
        return a;
    }

    @Test
    void 긴_본문은_줄_경계에서_나누고_앞부분을_겹친다() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 60; i++) body.append("line ").append(i).append(' ').append("x".repeat(40)).append('\n');
        List<String> parts = NoteIndexService.chunk(body.toString());

        assertThat(parts).hasSizeGreaterThan(1);
        assertThat(parts).allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(NoteIndexService.CHUNK_CHARS));
        String tailOfFirst = parts.get(0).substring(parts.get(0).length() - 50);
        assertThat(parts.get(1)).contains(tailOfFirst.substring(tailOfFirst.indexOf('\n') + 1));
        assertThat(NoteIndexService.chunk("짧은 메모")).containsExactly("짧은 메모");
    }

    @Test
    void AI_분석_JSON_은_키_값_줄로_펼친다() {
        String text = service.readable("""
                {"summary":"어깨 위주 세션","highlights":["전면/측면 고르게","폼 좋음"],"detail":{"volume":"적정"},"empty":null}""");
        assertThat(text).isEqualTo("summary: 어깨 위주 세션\nhighlights: 전면/측면 고르게; 폼 좋음\ndetail.volume: 적정");
        assertThat(service.readable("그냥 글")).isEqualTo("그냥 글");
    }

    @Test
    void 바뀐_조각만_임베딩하고_사라진_조각은_지운다() {
        when(embedder.isConfigured()).thenReturn(true);
        when(treatments.findAllForList(1L)).thenReturn(List.of(treatment(1L, "1달 통원치료")));
        when(analyses.findByUserId(1L)).thenReturn(List.of(analysis(9L, "{\"summary\":\"새 분석\"}")));
        when(embedder.embed(anyString(), eq(GeminiEmbeddingClient.Task.RETRIEVAL_DOCUMENT))).thenReturn(new float[]{1, 0, 0});

        // 치료 조각은 본문이 같아 그대로, 분석 조각은 새로, 예전 진료 조각(id 3)은 원본이 사라짐
        var docs = service.collect(1L);
        String treatmentText = docs.get(0).title() + "\n" + NoteIndexService.chunk(docs.get(0).body()).get(0);
        NoteChunk same = chunk("treatment", 1L, NoteIndexService.sha256("gemini-embedding-001|3|" + treatmentText));
        NoteChunk stale = chunk("visit", 3L, "old");
        when(chunks.findByUserId(1L)).thenReturn(new ArrayList<>(List.of(same, stale)));

        var result = service.reindex(1L);

        assertThat(result).isEqualTo(new NoteIndexService.IndexResult(2, 2, 1, 1, 1, 0));
        verify(embedder, times(1)).embed(anyString(), any());
        ArgumentCaptor<NoteChunk> saved = ArgumentCaptor.forClass(NoteChunk.class);
        verify(chunks).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo("ai_analysis");
        assertThat(saved.getValue().getText()).startsWith("과거 AI 분석 [exercise_session]").contains("summary: 새 분석");
        assertThat(saved.getValue().getChunkDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NoteChunk>> deleted = ArgumentCaptor.forClass(Collection.class);
        verify(chunks).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(stale);
    }

    @Test
    void 임베딩이_실패한_조각은_세고_나머지는_계속한다() {
        when(embedder.isConfigured()).thenReturn(true);
        when(treatments.findAllForList(1L)).thenReturn(List.of(treatment(1L, "메모")));
        when(chunks.findByUserId(1L)).thenReturn(new ArrayList<>());
        when(embedder.embed(anyString(), any())).thenThrow(new IllegalStateException("HTTP 429"));

        assertThat(service.reindex(1L).failed()).isEqualTo(1);
        verify(chunks, never()).save(any());
    }

    @Test
    void 검색은_내적_순으로_고르고_출처와_기간으로_거른다() {
        when(embedder.isConfigured()).thenReturn(true);
        when(embedder.embed("무릎", GeminiEmbeddingClient.Task.RETRIEVAL_QUERY)).thenReturn(new float[]{1, 0, 0});
        NoteChunk near = chunk("treatment", 1L, "h", new float[]{0.9f, 0.1f, 0}, LocalDate.of(2026, 10, 5));
        NoteChunk far = chunk("ai_analysis", 2L, "h", new float[]{0, 1, 0}, LocalDate.of(2026, 10, 27));
        NoteChunk old = chunk("visit", 3L, "h", new float[]{1, 0, 0}, LocalDate.of(2025, 1, 1));
        when(chunks.findByUserId(1L)).thenReturn(List.of(near, far, old));

        assertThat(service.search(1L, "무릎", null, null, null, 2)).extracting(NoteIndexService.Hit::sourceId).containsExactly(3L, 1L);
        assertThat(service.search(1L, "무릎", Set.of("ai_analysis"), null, null, 5)).extracting(NoteIndexService.Hit::sourceId).containsExactly(2L);
        assertThat(service.search(1L, "무릎", null, LocalDate.of(2026, 1, 1), null, 5)).extracting(NoteIndexService.Hit::sourceId).containsExactly(1L, 2L);
    }

    @Test
    void 임베딩이_꺼져_있으면_외부_호출_없이_본문만_색인하고_키워드로_찾는다() {
        when(embedder.isConfigured()).thenReturn(false);
        when(treatments.findAllForList(1L)).thenReturn(List.of(treatment(1L, "1달 통원치료, 주사치료와 체외충격파")));
        when(chunks.findByUserId(1L)).thenReturn(new ArrayList<>());

        assertThat(service.reindex(1L).embedded()).isEqualTo(1);
        verify(embedder, never()).embed(anyString(), any());
        ArgumentCaptor<NoteChunk> saved = ArgumentCaptor.forClass(NoteChunk.class);
        verify(chunks).save(saved.capture());
        assertThat(saved.getValue().getEmbedding()).isNull();
        assertThat(saved.getValue().getModel()).isNull();

        NoteChunk knee = saved.getValue();
        NoteChunk other = chunk("ai_analysis", 2L, "h", new float[]{0, 0, 1}, null);
        other.setText("어깨 위주 PT 세션, 레터럴 레이즈 횟수 조절");
        when(chunks.findByUserId(1L)).thenReturn(List.of(other, knee));

        // 조사가 붙은 말(무릎이)·띄어쓰기가 달라도 2글자 겹침으로 찾는다
        var hits = service.search(1L, "무릎이 아플때 충격파 치료", null, null, null, 5);
        assertThat(hits).extracting(NoteIndexService.Hit::source).containsExactly("treatment");
        assertThat(service.mode()).isEqualTo("keyword");
        verify(embedder, never()).embed(anyString(), any());
    }

    @Test
    void 키워드_점수는_단어와_2글자_겹침을_섞는다() {
        var q = KeywordScorer.of("장경인대 운동 제한");
        assertThat(q.score("운동 제한·권고: 장경인대 회복 전까지 고강도 금지")).isEqualTo(1.0);
        assertThat(q.score("어깨 운동")).isBetween(0.2, 0.5);
        assertThat(q.score("전혀 다른 내용")).isZero();
    }

    @Test
    void 벡터는_바이트로_저장했다가_그대로_읽는다() {
        float[] v = {0.25f, -1.5f, 3.0f};
        assertThat(NoteIndexService.fromBytes(NoteIndexService.toBytes(v))).containsExactly(v);
    }

    private static NoteChunk chunk(String source, long id, String hash) {
        return chunk(source, id, hash, new float[]{0, 0, 1}, null);
    }

    private static NoteChunk chunk(String source, long id, String hash, float[] v, LocalDate date) {
        NoteChunk c = new NoteChunk();
        c.setSource(source);
        c.setSourceId(id);
        c.setChunkNo(0);
        c.setContentHash(hash);
        c.setTitle(source + id);
        c.setText("text " + id);
        c.setDims(v.length);
        c.setEmbedding(NoteIndexService.toBytes(v));
        c.setChunkDate(date);
        return c;
    }
}

package com.lifemetrics.backend.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini API(AI Studio) 임베딩. 서버 .env 의 GEMINI_API_KEY 를 쓴다.
 * POST https://generativelanguage.googleapis.com/v1beta/models/{model}:embedContent
 * gemini-embedding-001 은 3072 가 아닌 차원이면 정규화되지 않은 벡터를 주므로 여기서 L2 정규화한다.
 * 개인정보: AI Studio 무료 등급은 입력이 제품 개선에 쓰일 수 있다 → 키의 프로젝트에 결제를 연결해 유료 등급으로 쓸 것.
 */
@Slf4j
@Component
public class GeminiEmbeddingClient {

    public enum Task { RETRIEVAL_DOCUMENT, RETRIEVAL_QUERY }

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_RETRIES = 4;

    private final String apiKey;
    private final String model;
    private final int dims;
    private final ObjectMapper objectMapper;
    private final RestTemplate rest;

    public GeminiEmbeddingClient(@Value("${GEMINI_API_KEY:${gemini.api-key:}}") String apiKey,
                                 @Value("${rag.embedding.model:gemini-embedding-001}") String model,
                                 @Value("${rag.embedding.dims:768}") int dims,
                                 ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.dims = dims;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(30_000);
        this.rest = new RestTemplate(f);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    public int dims() {
        return dims;
    }

    /** 텍스트 하나의 정규화된 벡터. 429·5xx 는 지수 백오프로 재시도, 그래도 실패하면 IllegalStateException. */
    public float[] embed(String text, Task task) {
        if (!isConfigured()) throw new IllegalStateException("GEMINI_API_KEY 가 설정되지 않았습니다.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", Map.of("parts", List.of(Map.of("text", text))));
        body.put("taskType", task.name());
        body.put("outputDimensionality", dims);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        long backoff = 1_000;
        for (int attempt = 1; ; attempt++) {
            try {
                String raw = rest.postForObject(BASE + model + ":embedContent", new HttpEntity<>(body, headers), String.class);
                return normalize(values(objectMapper.readTree(raw)));
            } catch (HttpClientErrorException e) {
                boolean retry = e.getStatusCode().value() == 429 && attempt < MAX_RETRIES;
                if (!retry) throw new IllegalStateException("Gemini 임베딩 실패 (HTTP " + e.getStatusCode().value() + ")", e);
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                if (attempt >= MAX_RETRIES) throw new IllegalStateException("Gemini 임베딩 실패 (HTTP " + e.getStatusCode().value() + ")", e);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Gemini 임베딩 실패: " + e.getMessage(), e);
            }
            log.info("Gemini 임베딩 재시도 {}/{} ({}ms 대기)", attempt, MAX_RETRIES, backoff);
            sleep(backoff);
            backoff *= 2;
        }
    }

    static float[] values(JsonNode res) {
        JsonNode v = res.path("embedding").path("values");
        if (!v.isArray()) v = res.path("embeddings").path(0).path("values");
        if (!v.isArray() || v.isEmpty()) throw new IllegalStateException("Gemini 임베딩 응답에 values 가 없습니다.");
        float[] out = new float[v.size()];
        for (int i = 0; i < out.length; i++) out[i] = (float) v.get(i).asDouble();
        return out;
    }

    static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += (double) x * x;
        double norm = Math.sqrt(sum);
        if (norm == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("임베딩 대기 중 중단됨", e);
        }
    }
}

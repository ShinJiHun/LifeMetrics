package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vertex AI (Google Cloud) Gemini 호출 클라이언트.
 * 기존 AI Studio(generativelanguage.googleapis.com) 호출을 대체한다.
 *   - chat()        : 페르소나 멀티턴 채팅 (system instruction에 프로필/RAG 컨텍스트 주입)
 *   - generateJson(): 라이딩 분석용 구조화 JSON (responseSchema로 형태 강제)
 *
 * 엔드포인트: POST {host}/v1/projects/{project}/locations/{location}/publishers/google/models/{model}:generateContent
 *   - location == "global" → host = https://aiplatform.googleapis.com
 *   - 그 외(예: us-central1) → host = https://{location}-aiplatform.googleapis.com
 * 인증     : Authorization: Bearer {ADC 토큰}  (API 키 미사용)
 *
 * ✅ Vertex AI는 (AI Studio 무료 티어와 달리) 입력 데이터를 모델 학습에 사용하지 않는다.
 *    페르소나 RAG처럼 개인 데이터(블로그/이력서)를 다루는 용도에 적합.
 *
 * 인증(ADC) 동작:
 *   - GCP riding-instance 위: 인스턴스에 붙은 서비스 계정 자격증명을 메타데이터 서버에서 자동 획득.
 *     → 서비스 계정에 roles/aiplatform.user(Vertex AI User) 부여 필요. 키 파일 불필요.
 *   - 로컬(MacBook) 개발: 터미널에서 `gcloud auth application-default login` 한 번 실행하면 동작.
 *   - (대안) GOOGLE_APPLICATION_CREDENTIALS 환경변수에 SA 키 JSON 경로 지정.
 */
@Service
public class GeminiService {

    /** GCP 프로젝트 ID (필수). 예: my-lifemetrics-prj */
    @Value("${vertex.project-id:}")
    private String projectId;

    /** 리전. 글로벌 엔드포인트는 "global". 리전 고정 시 us-central1 등. */
    @Value("${vertex.location:global}")
    private String location;

    /** Vertex 모델 ID. 예: gemini-2.5-flash / gemini-3-flash */
    @Value("${vertex.model:gemini-2.5-flash}")
    private String model;

    private final ObjectMapper om = new ObjectMapper();
    private final RestTemplate rest;

    /** ADC 자격증명. 토큰 만료 시 refreshIfExpired()가 알아서 갱신한다(스레드 안전). */
    private GoogleCredentials credentials;

    public GeminiService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);   // 5s
        f.setReadTimeout(60_000);     // 60s (분석/장문 응답 여유)
        this.rest = new RestTemplate(f);
    }

    @PostConstruct
    void initCredentials() {
        try {
            this.credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        } catch (Exception e) {
            // ADC를 찾지 못하면 credentials = null. hasApiKey()가 false를 반환해 호출부에서 503 등으로 처리.
            System.out.println("⚠️ [GeminiService] ADC 초기화 실패(인증 미설정): " + e.getMessage());
            this.credentials = null;
        }
    }

    /** 호출 가능 여부(프로젝트 ID + 자격증명 준비). 기존 컨트롤러 인터페이스 유지를 위해 이름은 그대로 둔다. */
    public boolean hasApiKey() {
        return credentials != null && projectId != null && !projectId.isBlank();
    }

    /** 채팅 한 턴. role 은 "user" | "assistant"(=model) | "model". */
    public record ChatMessage(String role, String content) {}

    /**
     * 페르소나 멀티턴 채팅.
     * @param systemPrompt 페르소나 프로필 + 검색된 블로그/이력서 컨텍스트(RAG)
     * @param history      user/assistant 교대 대화 이력
     * @return 모델 응답 텍스트. 실패/미설정 시 null (호출부에서 503 등으로 처리).
     */
    public String chat(String systemPrompt, List<ChatMessage> history) {
        return chat(systemPrompt, history, 2048);
    }

    /**
     * @param maxOutputTokens 출력 상한. thinking 지원 모델(2.5/3.x Flash)은 내부 사고에도
     *        출력 토큰을 소비하므로 너무 낮으면 답이 비어 올 수 있다. 짧은 답이라도 여유있게(≥1500) 잡는다.
     */
    public String chat(String systemPrompt, List<ChatMessage> history, int maxOutputTokens) {
        if (!hasApiKey() || history == null || history.isEmpty()) return null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                body.put("systemInstruction",
                        Map.of("parts", List.of(Map.of("text", systemPrompt))));
            }

            List<Map<String, Object>> contents = new ArrayList<>();
            for (ChatMessage m : history) {
                // Gemini 는 assistant 를 "model" 로 표기한다.
                String role = "assistant".equalsIgnoreCase(m.role()) || "model".equalsIgnoreCase(m.role())
                        ? "model" : "user";
                contents.add(Map.of(
                        "role", role,
                        "parts", List.of(Map.of("text", m.content()))
                ));
            }
            body.put("contents", contents);

            if (maxOutputTokens > 0) {
                body.put("generationConfig", Map.of("maxOutputTokens", maxOutputTokens));
            }

            return extractText(call(body));
        } catch (Exception e) {
            System.out.println("❌ [GeminiService] chat 에러: " + e.getMessage());
            return null;
        }
    }

    /**
     * 구조화 JSON 출력(라이딩 분석용). responseSchema 로 응답 형태를 강제한다.
     * @param prompt 분석 지시문(라이딩 데이터 포함)
     * @param schema OpenAPI 서브셋 스키마(Map). null 이면 자유 JSON.
     * @return JSON 문자열. 실패/미설정 시 null.
     */
    public String generateJson(String prompt, Map<String, Object> schema) {
        if (!hasApiKey() || prompt == null || prompt.isBlank()) return null;
        try {
            Map<String, Object> genConfig = new LinkedHashMap<>();
            genConfig.put("responseMimeType", "application/json");
            if (schema != null) genConfig.put("responseSchema", schema);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", prompt))
            )));
            body.put("generationConfig", genConfig);

            return extractText(call(body));
        } catch (Exception e) {
            System.out.println("❌ [GeminiService] generateJson 에러: " + e.getMessage());
            return null;
        }
    }

    // ── 내부 ──────────────────────────────────────────────────────────

    /** Vertex 엔드포인트 URL 조립. */
    private String endpointUrl() {
        String host = "global".equalsIgnoreCase(location)
                ? "https://aiplatform.googleapis.com"
                : "https://" + location + "-aiplatform.googleapis.com";
        return host + "/v1/projects/" + projectId
                + "/locations/" + location
                + "/publishers/google/models/" + model + ":generateContent";
    }

    /** ADC 토큰. 만료 시 자동 갱신 후 access token 문자열 반환. */
    private String accessToken() throws Exception {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private JsonNode call(Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken());   // Authorization: Bearer {ADC 토큰}

        HttpEntity<String> req = new HttpEntity<>(om.writeValueAsString(body), headers);
        ResponseEntity<String> res = rest.postForEntity(endpointUrl(), req, String.class);
        return om.readTree(res.getBody());
    }

    /** candidates[0].content.parts[*].text 합쳐서 반환. */
    private String extractText(JsonNode resp) {
        if (resp == null) return null;
        JsonNode parts = resp.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : parts) {
            if (p.hasNonNull("text")) sb.append(p.get("text").asText());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}

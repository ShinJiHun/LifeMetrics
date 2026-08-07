package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 로컬 Ollama({@code http://localhost:11434}) 호출 클라이언트.
 * <p>
 * Vertex(Gemini) ADC가 없는 로컬 개발 환경에서 {@link PersonaChatService}의
 * 대체 LLM으로 쓴다. prod는 계속 Gemini(Vertex)를 쓴다.
 */
@Service
public class OllamaService {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:qwen2.5:7b}")
    private String model;

    private final ObjectMapper om = new ObjectMapper();
    private final RestTemplate rest;

    public OllamaService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2_000);
        f.setReadTimeout(120_000); // 로컬 CPU 추론이라 여유있게
        this.rest = new RestTemplate(f);
    }

    /** Ollama 서버가 응답하는지 확인. */
    public boolean isAvailable() {
        try {
            rest.getForEntity(baseUrl + "/api/tags", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 멀티턴 채팅. {@link GeminiService.ChatMessage}를 그대로 재사용해
     * {@link PersonaChatService}가 두 제공자를 갈아끼우기 쉽게 한다.
     */
    public String chat(String systemPrompt, List<GeminiService.ChatMessage> history) {
        if (history == null || history.isEmpty()) return null;
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            for (GeminiService.ChatMessage m : history) {
                String role = "assistant".equalsIgnoreCase(m.role()) || "model".equalsIgnoreCase(m.role())
                        ? "assistant" : "user";
                messages.add(Map.of("role", role, "content", m.content()));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>(om.writeValueAsString(body), headers);

            ResponseEntity<String> res = rest.postForEntity(baseUrl + "/api/chat", req, String.class);
            JsonNode json = om.readTree(res.getBody());
            String text = json.path("message").path("content").asText(null);
            return (text != null && !text.isBlank()) ? text : null;
        } catch (Exception e) {
            System.out.println("❌ [OllamaService] chat 에러: " + e.getMessage());
            return null;
        }
    }
}
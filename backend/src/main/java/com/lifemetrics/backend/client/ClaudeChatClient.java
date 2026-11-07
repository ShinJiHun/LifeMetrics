package com.lifemetrics.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Anthropic Messages API 로 여러 턴 대화를 보낸다(system + messages).
 * 키는 AiAnalysisService 와 같은 anthropic.api-key 를 쓴다.
 */
@Slf4j
@Component
public class ClaudeChatClient {

    private static final String URL = "https://api.anthropic.com/v1/messages";

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public ClaudeChatClient(@Value("${anthropic.api-key:}") String apiKey,
                            @Value("${anthropic.chat-model:claude-sonnet-5}") String model,
                            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public record Message(String role, String content) {}

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 답변 텍스트. 키가 없거나 호출이 실패하면 empty. */
    public Optional<String> chat(String system, List<Message> messages, int maxTokens) {
        if (!isConfigured()) return Optional.empty();

        List<Map<String, String>> body = new ArrayList<>();
        for (Message m : messages) {
            body.add(Map.of("role", m.role(), "content", m.content()));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("max_tokens", maxTokens);
        request.put("system", system);
        request.put("messages", body);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        try {
            String raw = restTemplate.postForObject(URL, new HttpEntity<>(request, headers), String.class);
            JsonNode res = objectMapper.readTree(raw);
            StringBuilder text = new StringBuilder();
            for (JsonNode block : res.path("content")) {
                if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
            }
            if ("max_tokens".equals(res.path("stop_reason").asText())) {
                log.warn("Claude 대화 응답이 max_tokens 에서 잘림");
            }
            return text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
        } catch (Exception e) {
            log.warn("Claude 대화 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

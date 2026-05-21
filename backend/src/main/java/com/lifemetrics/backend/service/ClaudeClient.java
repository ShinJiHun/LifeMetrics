package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 호출 공용 클라이언트(신규 페르소나 기능용).
 * 기존 ActivityChatService/AiAnalysisService 는 자체 구현을 유지한다(범위 밖).
 */
@Component
@RequiredArgsConstructor
public class ClaudeClient {

    private final ObjectMapper objectMapper;

    @Value("${anthropic.api-key:}")
    private String anthropicApiKey;

    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    public boolean hasApiKey() {
        return anthropicApiKey != null && !anthropicApiKey.isEmpty();
    }

    /** 단일 사용자 메시지 호출. */
    public String complete(String systemPrompt, String userText, int maxTokens) {
        return complete(systemPrompt, List.of(Map.of("role", "user", "content", userText)), maxTokens);
    }

    /** 멀티턴 메시지 호출. 실패 시 null 반환(호출부에서 fallback 처리). */
    public String complete(String systemPrompt, List<Map<String, String>> messages, int maxTokens) {
        if (!hasApiKey()) {
            return null;
        }
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", messages
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);
            JsonNode json = objectMapper.readTree(response.getBody());
            return json.get("content").get(0).get("text").asText();
        } catch (Exception e) {
            System.out.println("❌ [ClaudeClient] Claude API 에러: " + e.getMessage());
            return null;
        }
    }
}

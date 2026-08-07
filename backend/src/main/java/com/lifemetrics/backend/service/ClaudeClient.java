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

import java.util.Base64;
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

    private static final String MODEL = "claude-sonnet-5";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    public boolean hasApiKey() {
        return anthropicApiKey != null && !anthropicApiKey.isEmpty();
    }

    /** 단일 사용자 메시지 호출. */
    public String complete(String systemPrompt, String userText, int maxTokens) {
        return complete(systemPrompt, List.of(Map.of("role", "user", "content", userText)), maxTokens);
    }

    /**
     * 이미지 1장 + 지시문 호출. 이미지는 base64 로 인라인 전송한다.
     *
     * @param mediaType image/png, image/jpeg 등
     */
    public String completeWithImage(String systemPrompt, String userText,
                                    byte[] image, String mediaType, int maxTokens) {
        Map<String, Object> imageBlock = Map.of(
                "type", "image",
                "source", Map.of(
                        "type", "base64",
                        "media_type", mediaType,
                        "data", Base64.getEncoder().encodeToString(image)));

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(imageBlock, Map.of("type", "text", "text", userText)));

        return callApi(systemPrompt, List.of(message), maxTokens);
    }

    /** 멀티턴 메시지 호출. 실패 시 null 반환(호출부에서 fallback 처리). */
    public String complete(String systemPrompt, List<Map<String, String>> messages, int maxTokens) {
        return callApi(systemPrompt, messages, maxTokens);
    }

    /** 실제 호출부. 메시지 content 가 문자열이든 블록 배열이든 그대로 실어 보낸다. */
    private String callApi(String systemPrompt, List<?> messages, int maxTokens) {
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
            // content 는 블록 배열이고 첫 블록이 항상 text 는 아니다.
            // 확장 사고를 켠 모델은 thinking 블록을 먼저 넣으므로 text 블록을 찾아 써야 한다.
            for (JsonNode block : json.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    return block.path("text").asText();
                }
            }
            System.out.println("⚠️ [ClaudeClient] text 블록 없음: " + json.path("content"));
            return null;
        } catch (Exception e) {
            System.out.println("❌ [ClaudeClient] Claude API 에러: " + e.getMessage());
            return null;
        }
    }
}

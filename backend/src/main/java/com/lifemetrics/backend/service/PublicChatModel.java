package com.lifemetrics.backend.service;

import com.lifemetrics.backend.client.ClaudeChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 방문자가 쓰는 공개 챗(개발자 페르소나 챗, 라이딩 기록 챗)의 Claude 호출.
 * 공개라 호출이 많을 수 있어 저렴한 모델(기본 Haiku)을 쓴다. ai.public-chat.model 로 바꾼다.
 * 이력은 기존 Gemini 형식(GeminiService.ChatMessage)을 받아 Messages API 규칙에 맞게 정리한다.
 */
@Component
public class PublicChatModel {

    private final ClaudeChatClient claude;
    private final String model;

    public PublicChatModel(ClaudeChatClient claude, @Value("${ai.public-chat.model:claude-haiku-5-5}") String model) {
        this.claude = claude;
        this.model = model;
    }

    public boolean isConfigured() {
        return claude.isConfigured();
    }

    public String model() {
        return model;
    }

    public Optional<String> chat(String system, List<GeminiService.ChatMessage> history, int maxTokens) {
        List<ClaudeChatClient.Message> messages = toClaude(history);
        if (messages.isEmpty()) return Optional.empty();
        return claude.chat(system, messages, maxTokens, model);
    }

    /** model→assistant, 빈 메시지 제거, user 로 시작, 같은 역할이 이어지면 합친다. */
    static List<ClaudeChatClient.Message> toClaude(List<GeminiService.ChatMessage> history) {
        List<ClaudeChatClient.Message> out = new ArrayList<>();
        for (GeminiService.ChatMessage m : history) {
            String content = m.content() == null ? "" : m.content().strip();
            if (content.isEmpty()) continue;
            String role = "user".equalsIgnoreCase(m.role()) ? "user" : "assistant";
            if (out.isEmpty() && !role.equals("user")) continue;
            if (!out.isEmpty() && out.get(out.size() - 1).role().equals(role)) {
                ClaudeChatClient.Message prev = out.remove(out.size() - 1);
                out.add(new ClaudeChatClient.Message(role, prev.content() + "\n\n" + content));
            } else {
                out.add(new ClaudeChatClient.Message(role, content));
            }
        }
        return out;
    }
}

package com.lifemetrics.backend.service;

import com.lifemetrics.backend.client.ClaudeChatClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicChatModelTest {

    @Test
    void Gemini_형식_이력을_Claude_규칙에_맞게_정리한다() {
        var out = PublicChatModel.toClaude(List.of(
                new GeminiService.ChatMessage("model", "안녕하세요, 챗봇입니다"),
                new GeminiService.ChatMessage("user", "질문1"),
                new GeminiService.ChatMessage("user", "이어서"),
                new GeminiService.ChatMessage("assistant", " "),
                new GeminiService.ChatMessage("model", "답")));

        assertThat(out).containsExactly(
                new ClaudeChatClient.Message("user", "질문1\n\n이어서"),
                new ClaudeChatClient.Message("assistant", "답"));
    }

    @Test
    void 지정한_모델로_호출한다() {
        ClaudeChatClient claude = mock(ClaudeChatClient.class);
        when(claude.chat(anyString(), anyList(), anyInt(), anyString())).thenReturn(Optional.of("답변"));
        var model = new PublicChatModel(claude, "claude-haiku-5-5");

        assertThat(model.chat("sys", List.of(new GeminiService.ChatMessage("user", "q")), 100)).contains("답변");
        verify(claude).chat(eq("sys"), anyList(), eq(100), eq("claude-haiku-5-5"));
        assertThat(model.chat("sys", List.of(new GeminiService.ChatMessage("model", "인사만")), 100)).isEmpty();
    }
}

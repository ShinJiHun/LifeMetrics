package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.ai.AthleteAgentLoop;
import com.lifemetrics.backend.entity.AthleteChatMessage;
import com.lifemetrics.backend.entity.AthleteChatSession;
import com.lifemetrics.backend.repository.AthleteChatMessageRepository;
import com.lifemetrics.backend.repository.AthleteChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AthleteChatHistoryServiceTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private final AthleteChatSessionRepository sessions = mock(AthleteChatSessionRepository.class);
    private final AthleteChatMessageRepository messages = mock(AthleteChatMessageRepository.class);
    private final AthleteChatHistoryService service = new AthleteChatHistoryService(sessions, messages, OM);

    private void saveAssignsId(long id) {
        when(sessions.save(any())).thenAnswer(inv -> {
            AthleteChatSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(id);
            return s;
        });
    }

    @Test
    void 새_대화는_첫_질문으로_제목을_만들고_질문과_답변_근거를_저장한다() throws Exception {
        saveAssignsId(10L);
        var call = new AthleteAgentLoop.ToolCall("get_ride_stats", OM.readTree("{\"from\":\"2026-08-01\"}"), false);

        Long id = service.record(1L, null, "8월이랑 10월 라이딩 비교해줘", "tools", "8월이 더 많았어요", List.of(call));

        assertThat(id).isEqualTo(10L);
        ArgumentCaptor<AthleteChatSession> s = ArgumentCaptor.forClass(AthleteChatSession.class);
        verify(sessions).save(s.capture());
        assertThat(s.getValue().getTitle()).isEqualTo("8월이랑 10월 라이딩 비교해줘");
        assertThat(s.getValue().getMode()).isEqualTo("tools");
        assertThat(s.getValue().getUpdatedAt()).isNotNull();

        ArgumentCaptor<AthleteChatMessage> m = ArgumentCaptor.forClass(AthleteChatMessage.class);
        verify(messages, times(2)).save(m.capture());
        assertThat(m.getAllValues()).extracting(AthleteChatMessage::getRole).containsExactly("user", "assistant");
        assertThat(m.getAllValues().get(0).getToolCalls()).isNull();
        assertThat(m.getAllValues().get(1).getToolCalls())
                .isEqualTo("[{\"name\":\"get_ride_stats\",\"input\":{\"from\":\"2026-08-01\"},\"error\":false}]");
    }

    @Test
    void 기존_대화에_이어_저장하고_남의_대화_id_면_새로_만든다() {
        AthleteChatSession mine = new AthleteChatSession();
        mine.setId(5L);
        mine.setTitle("기존");
        when(sessions.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(mine));
        when(sessions.findByIdAndUserId(6L, 1L)).thenReturn(Optional.empty());
        saveAssignsId(11L);

        assertThat(service.record(1L, 5L, "이어서", "tools", "네", List.of())).isEqualTo(5L);
        assertThat(mine.getTitle()).isEqualTo("기존");
        assertThat(service.record(1L, 6L, "남의 대화", "tools", "네", List.of())).isEqualTo(11L);
    }

    @Test
    void 저장된_근거를_다시_읽는다() {
        AthleteChatSession s = new AthleteChatSession();
        s.setId(5L);
        when(sessions.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(s));
        AthleteChatMessage q = new AthleteChatMessage(), a = new AthleteChatMessage();
        q.setRole("user");
        q.setContent("질문");
        a.setRole("assistant");
        a.setContent("답");
        a.setToolCalls("[{\"name\":\"get_treatments\",\"input\":{},\"error\":true}]");
        when(messages.findBySessionIdOrderByIdAsc(5L)).thenReturn(List.of(q, a));

        var items = service.messages(1L, 5L).orElseThrow();

        assertThat(items).extracting(AthleteChatHistoryService.MessageItem::role).containsExactly("user", "assistant");
        assertThat(items.get(0).toolCalls()).isEmpty();
        assertThat(items.get(1).toolCalls()).singleElement()
                .satisfies(c -> {
                    assertThat(c.name()).isEqualTo("get_treatments");
                    assertThat(c.error()).isTrue();
                });
        assertThat(service.messages(1L, 99L)).isEmpty();
    }

    @Test
    void 제목은_공백을_정리하고_40자에서_자른다() {
        assertThat(AthleteChatHistoryService.title("  줄\n바꿈   있는  질문 ")).isEqualTo("줄 바꿈 있는 질문");
        assertThat(AthleteChatHistoryService.title("가".repeat(50))).isEqualTo("가".repeat(40) + "…");
        assertThat(AthleteChatHistoryService.title(" ")).isEqualTo("(빈 질문)");
    }
}

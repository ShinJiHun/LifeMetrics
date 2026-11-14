package com.lifemetrics.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.ai.tools.AthleteTool;
import com.lifemetrics.backend.ai.tools.AthleteToolRegistry;
import com.lifemetrics.backend.client.ClaudeChatClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AthleteAgentLoopTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final List<ClaudeChatClient.Message> HISTORY =
            List.of(new ClaudeChatClient.Message("user", "8월 라이딩 어땠어?"));

    /** 입력의 from 을 그대로 돌려주는 가짜 도구. from 이 "boom" 이면 런타임 예외. */
    static class EchoTool implements AthleteTool {
        public String name() { return "echo"; }
        public String description() { return "test"; }
        public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
        public String execute(JsonNode input, long userId, LocalDate today) {
            String from = input.path("from").asText();
            if ("boom".equals(from)) throw new IllegalStateException("db down");
            if ("bad".equals(from)) throw new IllegalArgumentException("from 형식이 잘못됐습니다");
            return "rides from " + from + " user " + userId;
        }
    }

    private static ClaudeChatClient.ToolTurn toolUse(String id, String name, String from) throws Exception {
        return new ClaudeChatClient.ToolTurn("tool_use", OM.readTree("""
                [{"type":"text","text":"조회해 볼게요"},
                 {"type":"tool_use","id":"%s","name":"%s","input":{"from":"%s"}}]""".formatted(id, name, from)));
    }

    private static ClaudeChatClient.ToolTurn endTurn(String text) throws Exception {
        return new ClaudeChatClient.ToolTurn("end_turn",
                OM.readTree("[{\"type\":\"text\",\"text\":\"" + text + "\"}]"));
    }

    private final ClaudeChatClient claude = mock(ClaudeChatClient.class);
    private final AthleteAgentLoop loop = new AthleteAgentLoop(claude, new AthleteToolRegistry(List.of(new EchoTool())));

    @SuppressWarnings("unchecked")
    private List<List<Map<String, Object>>> capturedMessages(int calls, boolean allowTools) {
        ArgumentCaptor<List<Map<String, Object>>> cap = ArgumentCaptor.forClass(List.class);
        verify(claude, times(calls)).chatWithTools(anyString(), cap.capture(), anyList(), anyInt(), eq(allowTools));
        return cap.getAllValues();
    }

    @Test
    void 도구를_실행해_결과를_붙여_다시_부르고_end_turn_이면_답한다() throws Exception {
        when(claude.chatWithTools(anyString(), anyList(), anyList(), anyInt(), eq(true)))
                .thenReturn(Optional.of(toolUse("t1", "echo", "2026-08-01")))
                .thenReturn(Optional.of(endTurn("8월에 3번 탔어요")));

        AthleteAgentLoop.Result r = loop.run("sys", HISTORY, 1L, TODAY);

        assertThat(r.reply()).isEqualTo("8월에 3번 탔어요");
        assertThat(r.toolCalls()).extracting(AthleteAgentLoop.ToolCall::name).containsExactly("echo");
        assertThat(r.toolCalls().get(0).error()).isFalse();

        // 두 번째 호출의 마지막 메시지 = user 턴의 tool_result (같은 id, 도구 출력)
        List<Map<String, Object>> second = capturedMessages(2, true).get(1);
        assertThat(second).hasSize(3);
        assertThat(second.get(1).get("role")).isEqualTo("assistant");
        Map<?, ?> result = (Map<?, ?>) ((List<?>) second.get(2).get("content")).get(0);
        assertThat(result.get("type")).isEqualTo("tool_result");
        assertThat(result.get("tool_use_id")).isEqualTo("t1");
        assertThat(result.get("content")).isEqualTo("rides from 2026-08-01 user 1");
        assertThat(result.containsKey("is_error")).isFalse();
    }

    @Test
    void 도구_예외와_모르는_도구는_is_error_로_돌려준다() throws Exception {
        when(claude.chatWithTools(anyString(), anyList(), anyList(), anyInt(), eq(true)))
                .thenReturn(Optional.of(toolUse("t1", "echo", "boom")))
                .thenReturn(Optional.of(toolUse("t2", "nope", "x")))
                .thenReturn(Optional.of(toolUse("t3", "echo", "bad")))
                .thenReturn(Optional.of(endTurn("확인하지 못했어요")));

        AthleteAgentLoop.Result r = loop.run("sys", HISTORY, 1L, TODAY);

        assertThat(r.reply()).isEqualTo("확인하지 못했어요");
        assertThat(r.toolCalls()).extracting(AthleteAgentLoop.ToolCall::error).containsExactly(true, true, true);
        List<Map<String, Object>> last = capturedMessages(4, true).get(3);
        Map<?, ?> unknown = (Map<?, ?>) ((List<?>) last.get(4).get("content")).get(0);
        assertThat(unknown.get("is_error")).isEqualTo(true);
        assertThat((String) unknown.get("content")).contains("알 수 없는 도구: nope").contains("echo");
        Map<?, ?> bad = (Map<?, ?>) ((List<?>) last.get(6).get("content")).get(0);
        assertThat((String) bad.get("content")).startsWith("입력 오류: from 형식");
    }

    @Test
    void 라운드를_넘기면_도구_없이_마무리_호출을_한다() throws Exception {
        when(claude.chatWithTools(anyString(), anyList(), anyList(), anyInt(), eq(true)))
                .thenReturn(Optional.of(toolUse("t", "echo", "2026-01-01")));
        when(claude.chatWithTools(anyString(), anyList(), anyList(), anyInt(), eq(false)))
                .thenReturn(Optional.of(endTurn("지금까지 데이터로 답할게요")));

        AthleteAgentLoop.Result r = loop.run("sys", HISTORY, 1L, TODAY);

        assertThat(r.reply()).isEqualTo("지금까지 데이터로 답할게요");
        assertThat(r.toolCalls()).hasSize(AthleteAgentLoop.MAX_ROUNDS);
        List<Map<String, Object>> fin = capturedMessages(1, false).get(0);
        List<?> lastContent = (List<?>) fin.get(fin.size() - 1).get("content");
        assertThat(((Map<?, ?>) lastContent.get(lastContent.size() - 1)).get("type")).isEqualTo("text");
    }

    @Test
    void max_tokens_로_잘리면_잘렸다고_덧붙인다() throws Exception {
        when(claude.chatWithTools(anyString(), anyList(), anyList(), anyInt(), eq(true)))
                .thenReturn(Optional.of(new ClaudeChatClient.ToolTurn("max_tokens",
                        OM.readTree("[{\"type\":\"text\",\"text\":\"계획을 보면\"}]"))));

        assertThat(loop.run("sys", HISTORY, 1L, TODAY).reply())
                .startsWith("계획을 보면").endsWith(AthleteAgentLoop.TRUNCATED_NOTE);
    }

    @Test
    void 모델_호출이_실패하면_reply_는_null() {
        when(claude.chatWithTools(anyString(), anyList(), anyList(), anyInt(), eq(true))).thenReturn(Optional.empty());
        assertThat(loop.run("sys", HISTORY, 1L, TODAY).reply()).isNull();
    }

    @Test
    void 마지막_도구에만_cache_control_을_단다() {
        var tools = new AthleteToolRegistry(List.of(new EchoTool())).apiTools();
        assertThat(tools.get(tools.size() - 1)).containsKey("cache_control");
    }
}

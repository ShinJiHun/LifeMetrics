package com.lifemetrics.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.ai.tools.AthleteTool;
import com.lifemetrics.backend.ai.tools.AthleteToolRegistry;
import com.lifemetrics.backend.client.ClaudeChatClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * tool-use 루프: 모델이 도구를 고르면 실행해서 결과를 붙여 다시 부르고, end_turn 이면 답을 돌려준다.
 * 라운드(MAX_ROUNDS)나 시간(DEADLINE)을 넘기면 도구 없이 한 번 더 불러 지금까지의 데이터로 답하게 한다.
 */
@Slf4j
@Component
public class AthleteAgentLoop {

    static final int MAX_ROUNDS = 6;
    static final Duration DEADLINE = Duration.ofSeconds(60);
    static final int MAX_TOKENS = 2000;

    /** 화면에 근거로 보여줄 도구 호출 한 건. */
    public record ToolCall(String name, JsonNode input, boolean error) {}

    /** reply 가 null 이면 모델 호출 자체가 실패한 것. */
    public record Result(String reply, List<ToolCall> toolCalls) {}

    private final ClaudeChatClient claude;
    private final AthleteToolRegistry registry;
    private final Clock clock;

    @Autowired  // 테스트용 Clock 생성자가 따로 있어 스프링이 쓸 생성자를 지정한다
    public AthleteAgentLoop(ClaudeChatClient claude, AthleteToolRegistry registry) {
        this(claude, registry, Clock.systemUTC());
    }

    AthleteAgentLoop(ClaudeChatClient claude, AthleteToolRegistry registry, Clock clock) {
        this.claude = claude;
        this.registry = registry;
        this.clock = clock;
    }

    public Result run(String system, List<ClaudeChatClient.Message> history, long userId, LocalDate today) {
        Instant deadline = clock.instant().plus(DEADLINE);
        List<Map<String, Object>> tools = registry.apiTools();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ClaudeChatClient.Message m : history) messages.add(message(m.role(), m.content()));
        List<ToolCall> calls = new ArrayList<>();

        for (int round = 0; round < MAX_ROUNDS && clock.instant().isBefore(deadline); round++) {
            var turn = claude.chatWithTools(system, messages, tools, MAX_TOKENS, true);
            if (turn.isEmpty()) return new Result(null, calls);
            if (!"tool_use".equals(turn.get().stopReason())) return new Result(text(turn.get().content()), calls);

            messages.add(message("assistant", turn.get().content()));
            messages.add(message("user", runTools(turn.get().content(), userId, today, calls)));
        }

        log.info("애슐리트 tool-use 한도 도달 (도구 {}회) → 도구 없이 마무리", calls.size());
        @SuppressWarnings("unchecked")
        List<Object> last = (List<Object>) messages.get(messages.size() - 1).get("content");
        last.add(Map.of("type", "text", "text",
                "도구 호출 한도에 도달했다. 더 조회하지 말고 지금까지 받은 데이터로 답하라. 부족한 부분은 부족하다고 밝혀라."));
        var fin = claude.chatWithTools(system, messages, tools, MAX_TOKENS, false);
        return new Result(fin.map(t -> text(t.content())).orElse(null), calls);
    }

    /** 응답의 tool_use 블록을 모두 실행해 tool_result 블록 목록을 만든다. 예외는 is_error 로 돌려 모델이 고치게 한다. */
    private List<Object> runTools(JsonNode content, long userId, LocalDate today, List<ToolCall> calls) {
        List<Object> results = new ArrayList<>();
        for (JsonNode block : content) {
            if (!"tool_use".equals(block.path("type").asText())) continue;
            String name = block.path("name").asText();
            JsonNode input = block.path("input");
            String output;
            boolean error = false;
            AthleteTool tool = registry.find(name).orElse(null);
            if (tool == null) {
                output = "알 수 없는 도구: " + name + ". 사용할 수 있는 도구: " + String.join(", ", registry.names());
                error = true;
            } else {
                try {
                    output = tool.execute(input, userId, today);
                } catch (IllegalArgumentException e) {
                    output = "입력 오류: " + e.getMessage();
                    error = true;
                } catch (Exception e) {
                    log.warn("애슐리트 도구 {} 실패: {}", name, e.getMessage());
                    output = "조회 중 오류가 났습니다. 다른 기간이나 도구로 시도하거나, 데이터를 확인하지 못했다고 답하라.";
                    error = true;
                }
            }
            log.info("애슐리트 도구 {} {} → {}자{}", name, input, output.length(), error ? " (오류)" : "");
            calls.add(new ToolCall(name, input, error));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", "tool_result");
            r.put("tool_use_id", block.path("id").asText());
            r.put("content", output);
            if (error) r.put("is_error", true);
            results.add(r);
        }
        return results;
    }

    private static Map<String, Object> message(String role, Object content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    static String text(JsonNode content) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) sb.append(block.path("text").asText());
        }
        return sb.toString();
    }
}

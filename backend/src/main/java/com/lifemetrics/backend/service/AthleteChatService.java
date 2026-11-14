package com.lifemetrics.backend.service;

import com.lifemetrics.backend.ai.AthleteAgentLoop;
import com.lifemetrics.backend.client.ClaudeChatClient;
import com.lifemetrics.backend.client.RunPodClient;
import com.lifemetrics.backend.dto.PersonaChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 애슐리트 페르소나 챗. 질문마다 최근 기록(AthleteContextService)을 시스템 프롬프트에 넣어 대화한다.
 * 모델은 athlete.chat.provider 로 고른다: claude(기본) | runpod. 같은 기록·질문으로 두 모델을 비교할 수 있다.
 * 방식은 athlete.chat.mode 로 고른다:
 *   context(기본) — 최근 기록 묶음을 통째로 시스템 프롬프트에 넣는다.
 *   tools         — 짧은 요약만 넣고, 모델이 필요한 기간·데이터를 도구(ai.tools)로 조회한다. Claude 에서만 동작.
 */
@Slf4j
@Service
public class AthleteChatService {

    static final long USER_ID = 1L;
    static final int MAX_HISTORY = 20;
    static final int MAX_TOKENS = 2000;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AthleteContextService contextService;
    private final ClaudeChatClient claude;
    private final RunPodClient runPod;
    private final String provider;
    private final AthleteAgentLoop agentLoop;
    private final String mode;

    public AthleteChatService(AthleteContextService contextService,
                              ClaudeChatClient claude,
                              RunPodClient runPod,
                              AthleteAgentLoop agentLoop,
                              @Value("${athlete.chat.provider:claude}") String provider,
                              @Value("${athlete.chat.mode:context}") String mode) {
        this.contextService = contextService;
        this.claude = claude;
        this.runPod = runPod;
        this.agentLoop = agentLoop;
        this.provider = provider.trim().toLowerCase(Locale.ROOT);
        this.mode = mode.trim().toLowerCase(Locale.ROOT);
    }

    /** 답변 + (tools 모드일 때) 근거로 쓴 도구 호출 목록. */
    public record ChatResult(String reply, List<AthleteAgentLoop.ToolCall> toolCalls) {
        static ChatResult of(String reply) { return new ChatResult(reply, List.of()); }
    }

    public ChatResult chat(List<PersonaChatRequest.Message> messages) {
        List<ClaudeChatClient.Message> history = normalize(messages);
        if (history.isEmpty()) return ChatResult.of("질문을 입력해 주세요.");

        LocalDate today = LocalDate.now(KST);
        if ("tools".equals(mode) && !"runpod".equals(provider)) {
            if (!claude.isConfigured()) return ChatResult.of("Claude API 키(anthropic.api-key)가 설정되지 않았습니다.");
            AthleteAgentLoop.Result r = agentLoop.run(
                    toolsSystemPrompt(contextService.buildBrief(USER_ID, today)), history, USER_ID, today);
            return new ChatResult(orFallback(r.reply()), r.toolCalls());
        }
        return ChatResult.of(chatWithContext(history, today));
    }

    private String chatWithContext(List<ClaudeChatClient.Message> history, LocalDate today) {
        String system = systemPrompt(contextService.build(USER_ID, today));

        if ("runpod".equals(provider)) {
            if (!runPod.isConfigured()) return "RunPod 가 설정되지 않았습니다. (athlete.chat.provider=runpod)";
            try {
                return orFallback(runPod.ask(combined(system, history)));
            } catch (Exception e) {
                log.warn("애슐리트 챗 RunPod 호출 실패: {}", e.getMessage());
                return "RunPod 호출에 실패했습니다. 잠시 후 다시 시도해 주세요.";
            }
        }
        if (!claude.isConfigured()) return "Claude API 키(anthropic.api-key)가 설정되지 않았습니다.";
        return orFallback(claude.chat(system, history, MAX_TOKENS).orElse(null));
    }

    /** 최근 MAX_HISTORY 개, 빈 메시지 제거, user 턴으로 시작하고 같은 역할이 이어지면 합친다(Messages API 규칙). */
    static List<ClaudeChatClient.Message> normalize(List<PersonaChatRequest.Message> messages) {
        List<ClaudeChatClient.Message> out = new ArrayList<>();
        if (messages == null) return out;
        int start = Math.max(0, messages.size() - MAX_HISTORY);
        for (PersonaChatRequest.Message m : messages.subList(start, messages.size())) {
            String content = m.getContent() == null ? "" : m.getContent().trim();
            if (content.isEmpty()) continue;
            String role = "user".equalsIgnoreCase(m.getRole()) ? "user" : "assistant";
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

    static String systemPrompt(String context) {
        return PERSONA + """

                # 신지훈의 기록
                """ + context;
    }

    /** tools 모드: 같은 페르소나·규칙 + 조회 지침. 기록은 짧은 요약만 넣고 나머지는 도구로. */
    static String toolsSystemPrompt(String brief) {
        return PERSONA + """
                - 기록은 아래 요약만 미리 주어진다. 그 밖의 수치는 반드시 도구로 조회한 뒤 답하고, 추측해서 채우지 마세요.
                - 질문에 필요한 기간만 조회하세요. 비교 질문이면 두 기간을 각각 조회하세요.
                - 신발·자세·통증·피팅 질문이면 get_profile_and_gear 를 먼저 부르세요.
                - 도구가 "(기록 없음)"을 돌려주면 그 기간 기록이 없다고 말하세요.

                # 신지훈의 기록 (요약)
                """ + brief;
    }

    private static final String PERSONA = """
                당신은 신지훈의 "애슐리트 페르소나" 코치입니다. 신지훈은 랜도너스(브레베 200~1000km 장거리 자전거)를 타고
                웨이트 트레이닝을 병행하며 체지방 감량을 하고 있습니다. 아래 기록을 근거로 신지훈 본인과 대화하며
                다이어트 전략, 훈련 계획, 회복, 대회 준비를 함께 고민해 주세요.

                규칙:
                - 수치는 아래 기록에 있는 것만 쓰세요. 기록에 없으면 없다고 말하고, 추정할 때는 추정이라고 밝히세요.
                - 긴 라이딩(10시간 이상, 1만kcal 이상)도 실제 대회 기록일 수 있으니 이상치로 취급하지 마세요.
                - 식단 기록은 끼니가 빠진 날이 많습니다. "빠진 끼니"가 있는 날의 섭취·수지는 실제 하루 섭취가 아닙니다.
                - 소모 칼로리는 기초대사량 + 활동 추정치라 오차가 있습니다. 하루 단위보다 며칠 흐름으로 판단하세요.
                - 치료·부상과 진료·처방 내역이 있으면 훈련·감량 조언에 반영하세요. 운동 제한·권고를 지키는 쪽으로 계획하고,
                  주사·체외충격파 같은 시술 직후이거나 진통제·근이완제를 복용 중이면 통증이 가려질 수 있다는 점을 고려하세요.
                  약의 효과·부작용·복용법 판단은 처방한 의사·약사에게 확인하라고 안내하세요.
                - 신체 치수·자전거 피팅 기록은 장거리 통증·자세·장비 질문에 근거로 쓰세요. 세팅 변경은 한 번에 몇 mm 씩
                  작게 제안하고, 큰 변경은 피터와 상의하라고 안내하세요.
                - 의학적 진단은 하지 말고, 극단적인 감량(하루 1000kcal 이상 적자 지속 등)은 권하지 마세요.
                - 정중한 대화체로, 보통 3~8문장. 계획을 물으면 짧은 목록을 써도 됩니다. 표와 코드블록은 쓰지 마세요.
                """;

    private static String combined(String system, List<ClaudeChatClient.Message> history) {
        StringBuilder sb = new StringBuilder(system).append("\n\n");
        for (ClaudeChatClient.Message m : history) {
            sb.append("user".equals(m.role()) ? "사용자: " : "코치: ").append(m.content()).append('\n');
        }
        return sb.append("코치:").toString();
    }

    private static String orFallback(String reply) {
        return reply != null && !reply.isBlank() ? reply.trim()
                : "죄송합니다. 답변을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.";
    }
}

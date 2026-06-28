package com.lifemetrics.backend.notify;

import com.lifemetrics.backend.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 사용자가 입력한 "해야 할 일" 목록을 AI로 정리해 Slack 알림으로 전송한다.
 * <p>
 * 전송 메시지는 요구사항에 따라 반드시 "확인" 문구로 시작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoNotifyService {

    private final GeminiService geminiService;
    private final SlackNotifier slackNotifier;

    /** 전송 메시지 접두 — 반드시 이 문구로 시작한다. */
    private static final String PREFIX = "확인";

    /**
     * 할 일 목록을 AI로 정리한 뒤 Slack 으로 전송한다.
     *
     * @param items 정리된(공백/빈값 제거) 할 일 목록
     * @return Slack 전송 성공 여부
     */
    public boolean processAndNotify(List<String> items) {
        String organized = organizeWithAi(items);
        String message = PREFIX + " — 오늘 해야 할 일\n\n" + organized;
        return slackNotifier.send(message);
    }

    /**
     * Gemini로 할 일을 우선순위/카테고리별로 깔끔하게 정리한다.
     * API 키가 없거나 호출 실패 시 입력 목록을 그대로 나열한 텍스트로 폴백한다.
     */
    private String organizeWithAi(List<String> items) {
        String joined = items.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));

        if (!geminiService.hasApiKey()) {
            log.warn("Gemini API 키 미설정 — 할 일을 AI 정리 없이 그대로 전송합니다.");
            return joined;
        }

        String systemPrompt = """
                너는 사용자의 개인 비서다. 사용자가 입력한 '해야 할 일' 목록을 받아
                Slack 메시지로 보기 좋게 정리한다. 규칙:
                - 인사말/머리말 없이 정리된 할 일만 출력한다.
                - 비슷한 일은 묶고, 급하거나 중요한 일이 위로 오도록 정렬한다.
                - 각 항목은 한 줄로, 앞에 ✅ 같은 이모지나 • 불릿을 붙인다.
                - 사용자가 무엇을 해야 하는지 한눈에 알 수 있게 동사로 끝맺는다.
                - 한국어로, 간결하게.
                """;

        String userPrompt = "다음 할 일들을 정리해줘:\n" + joined;

        String ai = geminiService.chat(
                systemPrompt,
                List.of(new GeminiService.ChatMessage("user", userPrompt))
        );

        return (ai == null || ai.isBlank()) ? joined : ai.trim();
    }
}

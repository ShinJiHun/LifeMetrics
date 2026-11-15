package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.ai.AthleteAgentLoop;
import com.lifemetrics.backend.entity.AthleteChatMessage;
import com.lifemetrics.backend.entity.AthleteChatSession;
import com.lifemetrics.backend.repository.AthleteChatMessageRepository;
import com.lifemetrics.backend.repository.AthleteChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 애슐리트 챗 대화 저장. 질문 한 번마다 사용자 메시지와 답변(+근거 도구)을 한 쌍으로 남긴다.
 * 모델에 보내는 대화 이력은 지금처럼 화면이 보내고, 여기서는 기록과 다시 열기만 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AthleteChatHistoryService {

    static final int TITLE_MAX = 40;
    static final int LIST_MAX = 50;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AthleteChatSessionRepository sessionRepo;
    private final AthleteChatMessageRepository messageRepo;
    private final ObjectMapper objectMapper;

    public record SessionItem(Long id, String title, String mode, LocalDateTime updatedAt) {}

    public record MessageItem(String role, String content, List<AthleteAgentLoop.ToolCall> toolCalls) {}

    /**
     * 질문과 답변을 저장하고 대화 id 를 돌려준다. sessionId 가 없거나 남의 대화면 새 대화를 만든다.
     */
    @Transactional
    public Long record(long userId, Long sessionId, String question, String mode,
                       String reply, List<AthleteAgentLoop.ToolCall> toolCalls) {
        AthleteChatSession session = Optional.ofNullable(sessionId)
                .flatMap(id -> sessionRepo.findByIdAndUserId(id, userId))
                .orElseGet(() -> {
                    AthleteChatSession s = new AthleteChatSession();
                    s.setUserId(userId);
                    s.setTitle(title(question));
                    s.setMode(mode);
                    return s;
                });
        session.setUpdatedAt(LocalDateTime.now(KST));
        session = sessionRepo.save(session);

        messageRepo.save(message(session.getId(), "user", question, null));
        messageRepo.save(message(session.getId(), "assistant", reply,
                toolCalls == null || toolCalls.isEmpty() ? null : json(toolCalls)));
        return session.getId();
    }

    @Transactional(readOnly = true)
    public List<SessionItem> sessions(long userId) {
        return sessionRepo.findByUserIdOrderByUpdatedAtDescIdDesc(userId, PageRequest.of(0, LIST_MAX)).stream()
                .map(s -> new SessionItem(s.getId(), s.getTitle(), s.getMode(), s.getUpdatedAt()))
                .toList();
    }

    /** 대화의 메시지. 없거나 남의 대화면 empty. */
    @Transactional(readOnly = true)
    public Optional<List<MessageItem>> messages(long userId, long sessionId) {
        return sessionRepo.findByIdAndUserId(sessionId, userId).map(s ->
                messageRepo.findBySessionIdOrderByIdAsc(s.getId()).stream()
                        .map(m -> new MessageItem(m.getRole(), m.getContent(), toolCalls(m.getToolCalls())))
                        .toList());
    }

    @Transactional
    public boolean delete(long userId, long sessionId) {
        return sessionRepo.findByIdAndUserId(sessionId, userId).map(s -> {
            sessionRepo.delete(s); // 메시지는 FK ON DELETE CASCADE
            return true;
        }).orElse(false);
    }

    static String title(String question) {
        String t = question == null ? "" : question.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) return "(빈 질문)";
        return t.length() <= TITLE_MAX ? t : t.substring(0, TITLE_MAX) + "…";
    }

    private static AthleteChatMessage message(Long sessionId, String role, String content, String toolCalls) {
        AthleteChatMessage m = new AthleteChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content == null ? "" : content);
        m.setToolCalls(toolCalls);
        return m;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("도구 호출 기록 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    private List<AthleteAgentLoop.ToolCall> toolCalls(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return List.of(objectMapper.readValue(json, AthleteAgentLoop.ToolCall[].class));
        } catch (Exception e) {
            log.warn("도구 호출 기록 읽기 실패: {}", e.getMessage());
            return List.of();
        }
    }
}

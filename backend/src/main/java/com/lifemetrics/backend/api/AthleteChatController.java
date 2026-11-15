package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.AthleteChatRequest;
import com.lifemetrics.backend.dto.AthleteChatResponse;
import com.lifemetrics.backend.dto.PersonaChatRequest;
import com.lifemetrics.backend.security.AdminWriteFilter;
import com.lifemetrics.backend.service.AthleteChatHistoryService;
import com.lifemetrics.backend.service.AthleteChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 애슐리트 페르소나 챗. 건강·식단 기록을 다루므로 관리자만 쓴다
 * (POST/DELETE 는 AdminWriteFilter 가 관리자 세션을 요구한다. PUBLIC_WRITE_PATHS 에 넣지 말 것).
 * 대화 기록 조회(GET)는 필터가 막지 않으므로 여기서 직접 관리자인지 확인한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/athlete")
@RequiredArgsConstructor
public class AthleteChatController {

    static final long USER_ID = 1L;

    private final AthleteChatService chatService;
    private final AthleteChatHistoryService historyService;
    private final AdminWriteFilter adminWriteFilter;

    @PostMapping("/chat")
    public AthleteChatResponse chat(@RequestBody AthleteChatRequest request) {
        List<PersonaChatRequest.Message> messages = request.messages();
        AthleteChatService.ChatResult r = chatService.chat(messages);

        Long sessionId = request.sessionId();
        String question = lastUserMessage(messages);
        if (question != null) {
            try {
                sessionId = historyService.record(USER_ID, sessionId, question, chatService.mode(), r.reply(), r.toolCalls());
            } catch (Exception e) {
                // 기록 실패로 답변을 잃지 않게 한다
                log.warn("애슐리트 챗 대화 저장 실패: {}", e.getMessage());
            }
        }
        return new AthleteChatResponse(r.reply(), r.toolCalls().stream()
                .map(c -> new AthleteChatResponse.ToolCall(c.name(), c.input(), c.error()))
                .toList(), sessionId);
    }

    @GetMapping("/chat/sessions")
    public ResponseEntity<?> sessions(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) return forbidden();
        return ResponseEntity.ok(historyService.sessions(USER_ID));
    }

    @GetMapping("/chat/sessions/{id}")
    public ResponseEntity<?> session(HttpServletRequest request, @PathVariable long id) {
        if (!adminWriteFilter.isAdmin(request)) return forbidden();
        return historyService.messages(USER_ID, id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/chat/sessions/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        return historyService.delete(USER_ID, id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    static String lastUserMessage(List<PersonaChatRequest.Message> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            PersonaChatRequest.Message m = messages.get(i);
            if ("user".equalsIgnoreCase(m.getRole()) && m.getContent() != null && !m.getContent().isBlank()) {
                return m.getContent().strip();
            }
        }
        return null;
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "관리자만 접근할 수 있습니다."));
    }
}

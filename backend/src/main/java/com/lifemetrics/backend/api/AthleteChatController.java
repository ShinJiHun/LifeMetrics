package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.PersonaChatRequest;
import com.lifemetrics.backend.dto.PersonaChatResponse;
import com.lifemetrics.backend.service.AthleteChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 애슐리트 페르소나 챗. 건강·식단 기록을 다루므로 관리자만 쓴다
 * (POST 는 AdminWriteFilter 가 관리자 세션을 요구한다. PUBLIC_WRITE_PATHS 에 넣지 말 것).
 */
@RestController
@RequestMapping("/api/athlete")
@RequiredArgsConstructor
public class AthleteChatController {

    private final AthleteChatService chatService;

    @PostMapping("/chat")
    public PersonaChatResponse chat(@RequestBody PersonaChatRequest request) {
        return new PersonaChatResponse(chatService.chat(request.getMessages()));
    }
}

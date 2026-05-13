package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.ActivityChatRequest;
import com.lifemetrics.backend.dto.ActivityChatResponse;
import com.lifemetrics.backend.service.ActivityChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final ActivityChatService chatService;

    @PostMapping("/activity/{activityId}")
    public ActivityChatResponse chatAboutActivity(
            @RequestParam(defaultValue = "1") Long userId,
            @PathVariable Long activityId,
            @RequestBody ActivityChatRequest request) {
        String reply = chatService.chat(userId, activityId, request.getMessages());
        return new ActivityChatResponse(reply);
    }
}
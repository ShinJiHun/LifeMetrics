package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.ActivityChatRequest;
import com.lifemetrics.backend.service.ActivityChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final ActivityChatService activityChatService;

    @PostMapping("/activity/{activityId}")
    public Map<String, String> chatAboutActivity(
            @PathVariable Long activityId,
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody ActivityChatRequest request
    ) {
        String reply = activityChatService.chat(userId, activityId, request.getMessages());
        return Map.of("reply", reply);
    }
}

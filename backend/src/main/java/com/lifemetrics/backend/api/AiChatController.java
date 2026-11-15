package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.ActivityChatRequest;
import com.lifemetrics.backend.security.AdminWriteFilter;
import com.lifemetrics.backend.security.PublicChatRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import com.lifemetrics.backend.service.ActivityChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final ActivityChatService activityChatService;
    private final AdminWriteFilter adminWriteFilter;
    private final PublicChatRateLimiter rateLimiter;

    @PostMapping("/activity/{activityId}")
    public Map<String, String> chatAboutActivity(
            @PathVariable Long activityId,
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody ActivityChatRequest request,
            HttpServletRequest http
    ) {
        // 방문자도 쓰는 공개 챗이라 하루 사용량 제한(관리자 제외)
        if (!adminWriteFilter.isAdmin(http)) {
            PublicChatRateLimiter.Result limit = rateLimiter.tryAcquire(http.getRemoteAddr());
            if (limit != PublicChatRateLimiter.Result.OK) return Map.of("reply", PublicChatRateLimiter.message(limit));
        }
        String reply = activityChatService.chat(userId, activityId, request.getMessages());
        return Map.of("reply", reply);
    }
}

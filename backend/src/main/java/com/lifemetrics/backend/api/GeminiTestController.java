package com.lifemetrics.backend.api;

import com.lifemetrics.backend.service.GeminiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test/gemini")
public class GeminiTestController {

    private final GeminiService gemini;
    public GeminiTestController(GeminiService gemini) { this.gemini = gemini; }

    // 설정/인증 준비됐는지부터 확인
    @GetMapping("/ready")
    public Map<String, Object> ready() {
        return Map.of("ready", gemini.hasApiKey());
    }

    // 실제 호출
    @GetMapping("/chat")
    public Map<String, Object> chat(
            @RequestParam(defaultValue = "한 줄로 자기소개 해줘") String q) {
        String out = gemini.chat(
                "너는 친절한 사이클링 코치야.",
                List.of(new GeminiService.ChatMessage("user", q)));
        return Map.of("ok", out != null, "answer", out);
    }
}

package com.lifemetrics.backend.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 알림 전송 테스트용 엔드포인트.
 * <p>
 * 예: {@code curl -X POST localhost:8080/api/notify -H 'Content-Type: application/json' -d '{"text":"안녕"}'}
 */
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final SlackNotifier slackNotifier;

    @PostMapping
    public ResponseEntity<Map<String, Object>> notify(@RequestBody(required = false) NotifyRequest request) {
        String text = (request == null || request.getText() == null || request.getText().isBlank())
                ? "🔔 LifeMetrics 테스트 알림"
                : request.getText();

        boolean sent = slackNotifier.send(text);
        return ResponseEntity.ok(Map.of("sent", sent));
    }
}
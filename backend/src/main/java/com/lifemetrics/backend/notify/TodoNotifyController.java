package com.lifemetrics.backend.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 설정 화면에서 입력한 "해야 할 일"을 받아 AI 정리 후 Slack 으로 전송한다.
 * <p>
 * 진입 비밀번호는 {@code settings.password} (.env {@code SETTINGS_PASSWORD})로 설정한다.
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoNotifyController {

    private final TodoNotifyService todoNotifyService;

    @Value("${settings.password:lifemetrics}")
    private String settingsPassword;

    /** 비밀번호만 검증한다. (설정 진입 단계용) */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
        boolean ok = passwordMatches(body.get("password"));
        return ok
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false));
    }

    /** 비밀번호 검증 후 할 일을 AI로 정리해 Slack 전송한다. */
    @PostMapping("/notify")
    public ResponseEntity<Map<String, Object>> notify(@RequestBody TodoNotifyRequest request) {
        if (!passwordMatches(request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "비밀번호가 올바르지 않습니다."));
        }

        List<String> items = request.cleanItems();
        if (items.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "할 일을 하나 이상 입력하세요."));
        }

        boolean sent = todoNotifyService.processAndNotify(items);
        return ResponseEntity.ok(Map.of("sent", sent, "count", items.size()));
    }

    private boolean passwordMatches(String input) {
        return settingsPassword != null && settingsPassword.equals(input);
    }
}

package com.lifemetrics.backend.api;

import com.lifemetrics.backend.notify.SlackNotifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlackTestController {

    private final SlackNotifier slackNotifier;

    public SlackTestController(SlackNotifier slackNotifier) {
        this.slackNotifier = slackNotifier;
    }

    @GetMapping("/test/slack")
    public String testSlack() {
        slackNotifier.send("🚀 Spring Boot에서 보낸 테스트 메시지");
        return "sent";
    }
}
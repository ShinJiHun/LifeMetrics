package com.lifemetrics.backend.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Slack Incoming Webhook 으로 알림 메시지를 전송한다.
 * <p>
 * webhook URL 은 {@code slack.webhook-url} 프로퍼티(.env 의 {@code SLACK_WEBHOOK_URL})로 주입된다.
 * 설정이 비어 있으면 전송을 건너뛰고 false 를 반환하므로, URL 미설정 환경에서도 앱 동작에는 영향이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackNotifier {

    @Value("${slack.webhook-url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate;

    /**
     * Slack 채널로 텍스트 메시지를 전송한다.
     *
     * @param text 전송할 메시지(마크다운 형식 지원)
     * @return 전송 성공 여부. webhook 미설정이거나 전송 실패 시 false.
     */
    public boolean send(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Slack webhook URL이 설정되지 않아 알림을 건너뜁니다. (slack.webhook-url)");
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request =
                    new HttpEntity<>(Map.of("text", text), headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);
            return true;
        } catch (Exception e) {
            log.error("Slack 알림 전송 실패: {}", e.getMessage());
            return false;
        }
    }
}
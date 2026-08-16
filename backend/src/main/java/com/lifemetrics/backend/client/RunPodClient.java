package com.lifemetrics.backend.client;

import com.lifemetrics.backend.dto.RunPodRequest;
import com.lifemetrics.backend.dto.RunPodResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunPodClient {

    private final WebClient runPodWebClient;

    @Value("${runpod.endpoint-id}")
    private String endpointId;

    private static final int MAX_POLL_ATTEMPTS = 60;       // 최대 폴링 횟수
    private static final long POLL_INTERVAL_MS = 3000;     // 3초 간격

    /**
     * RunPod 서버리스 엔드포인트에 비동기 요청(/run)을 보낸 뒤,
     * /status/{id}를 폴링해 완료될 때까지 기다린다.
     * 콜드스타트 시 1~2분까지 걸릴 수 있어 최대 3분 정도 폴링한다.
     */
    public String ask(String prompt) {
        RunPodRequest request = RunPodRequest.ofPrompt(prompt);

        // 1. 비동기 제출
        RunPodResponse submitResponse = runPodWebClient.post()
                .uri("/{endpointId}/run", endpointId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RunPodResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        if (submitResponse == null || submitResponse.id() == null) {
            throw new RuntimeException("RunPod 작업 제출 실패");
        }

        String jobId = submitResponse.id();
        log.info("RunPod 작업 제출됨 - jobId: {}", jobId);

        // 2. 폴링
        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("RunPod 폴링 중단됨", e);
            }

            RunPodResponse statusResponse = runPodWebClient.get()
                    .uri("/{endpointId}/status/{jobId}", endpointId, jobId)
                    .retrieve()
                    .bodyToMono(RunPodResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (statusResponse == null) continue;

            String status = statusResponse.status();
            log.info("RunPod 상태 확인 ({}/{}) - status: {}", i + 1, MAX_POLL_ATTEMPTS, status);

            if ("COMPLETED".equals(status)) {
                String text = statusResponse.extractText();
                log.info("RunPod 응답 완료 - delay: {}ms, execution: {}ms",
                        statusResponse.delayTime(), statusResponse.executionTime());
                return text;
            }

            if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                throw new RuntimeException("RunPod 작업 실패: status=" + status);
            }
            // IN_QUEUE, IN_PROGRESS 면 계속 폴링
        }

        throw new RuntimeException("RunPod 응답 타임아웃 (폴링 " + MAX_POLL_ATTEMPTS + "회 초과)");
    }
}
package com.lifemetrics.backend.api;

import com.lifemetrics.backend.service.AthleteDatasetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * 애슐리트 파인튜닝 데이터셋 샘플 한 건: {@code POST /api/athlete/dataset/sample}
 * <p>
 * data_pipeline 의 생성 스크립트 전용. 관리자 쿠키 대신 {@code Authorization: Bearer <ATHLETE_DATASET_TOKEN>}
 * (AdminWriteFilter 공개 쓰기 경로이고 토큰 검사는 여기서). 토큰이 설정되지 않은 서버에서는 404 — 엔드포인트가 없는 것처럼.
 * Claude 호출 비용이 드는 경로라 동시에 {@link #MAX_CONCURRENT} 건까지만 받고 나머지는 429.
 */
@Slf4j
@RestController
@RequestMapping("/api/athlete/dataset")
@RequiredArgsConstructor
public class AthleteDatasetController {

    static final int MAX_CONCURRENT = 2;

    private final AthleteDatasetService service;
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT);

    @Value("${ATHLETE_DATASET_TOKEN:}")
    private String token;

    public record SampleBody(String mode, String date, String question) {
    }

    @PostMapping("/sample")
    public ResponseEntity<?> sample(HttpServletRequest request, @RequestBody(required = false) SampleBody body) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(404).body(Map.of("message", "Not Found"));
        }
        if (!validToken(request.getHeader("Authorization"))) {
            return ResponseEntity.status(401).body(Map.of("message", "토큰이 없거나 맞지 않습니다."));
        }
        if (body == null) return ResponseEntity.badRequest().body(Map.of("message", "요청 본문이 필요합니다."));

        LocalDate date;
        try {
            date = body.date() == null ? null : LocalDate.parse(body.date().strip());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "date 는 YYYY-MM-DD 형식입니다."));
        }

        if (!slots.tryAcquire()) {
            return ResponseEntity.status(429).body(Map.of("message", "동시에 처리할 수 있는 요청 수를 넘었습니다. 잠시 후 다시 시도하세요."));
        }
        try {
            return ResponseEntity.ok(service.sample(body.mode(), date, body.question()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("애슐리트 데이터셋 샘플 실패: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        } finally {
            slots.release();
        }
    }

    private boolean validToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                header.substring("Bearer ".length()).trim().getBytes(StandardCharsets.UTF_8));
    }
}

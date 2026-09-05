package com.lifemetrics.backend.lotto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.lotto.dto.LottoTicketDto;
import com.lifemetrics.backend.lotto.dto.LottoTicketUploadResponse;
import com.lifemetrics.backend.lotto.entity.LottoTicketEntity;
import com.lifemetrics.backend.lotto.repository.LottoTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 구매한 로또 용지 사진을 업로드하면
 * 1) Claude vision API로 회차/게임별 번호를 읽고
 * 2) 원본 사진을 NAS에 저장하고
 * 3) 인식된 게임들을 lotto_ticket 에 저장한다.
 *
 * FitdaysOcrService 와 동일한 패턴(이미지 → base64 → Claude Messages API → JSON 파싱)을 따른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
public class LottoTicketOcrService {

    private final LottoTicketRepository ticketRepo;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${lotto.nas-path:/mnt/200gb/NAS/data/lotto}")
    private String nasPath;

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String PROMPT = """
            이 이미지는 한국 로또 6/45 용지(구매 영수증) 사진입니다. 다음 정보를 JSON으로 반환해주세요:

            - round: 용지에 인쇄된 추첨 회차 번호 (정수, 못 찾으면 null)
            - games: 용지에 있는 각 게임(A, B, C, D, E 중 존재하는 것만)의 배열.
              각 원소는 다음 형식입니다:
              { "label": "A", "numbers": [정수 6개, 오름차순 아님 인쇄된 순서 그대로] }

            자동/수동 여부는 무시하고 번호만 정확히 읽어주세요.
            번호는 1~45 범위의 정수여야 합니다.
            JSON만 반환하고 다른 텍스트는 포함하지 마세요. 예:
            {"round": 1234, "games": [{"label":"A","numbers":[3,11,19,22,34,41]}]}
            """;

    public LottoTicketUploadResponse upload(MultipartFile file) {
        try {
            byte[] imageBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mediaType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

            String jsonResponse = callClaude(base64Image, mediaType);
            log.info("Claude 로또 OCR 응답: {}", jsonResponse);
            JsonNode data = parseResponse(jsonResponse);

            Integer round = data.hasNonNull("round") ? data.get("round").asInt() : null;
            JsonNode gamesNode = data.get("games");
            if (gamesNode == null || !gamesNode.isArray() || gamesNode.isEmpty()) {
                return LottoTicketUploadResponse.fail("용지에서 게임 번호를 인식하지 못했습니다.");
            }

            String imagePath = saveToNas(file, round);
            String ticketGroup = UUID.randomUUID().toString();

            List<LottoTicketDto> saved = new ArrayList<>();
            int gameNo = 1;
            for (JsonNode game : gamesNode) {
                int[] numbers = toNumbers(game.get("numbers"));
                if (numbers == null) continue;

                LottoTicketEntity entity = new LottoTicketEntity();
                entity.setTicketGroup(ticketGroup);
                entity.setRound(round);
                entity.setGameNo(gameNo++);
                entity.setN1(numbers[0]);
                entity.setN2(numbers[1]);
                entity.setN3(numbers[2]);
                entity.setN4(numbers[3]);
                entity.setN5(numbers[4]);
                entity.setN6(numbers[5]);
                entity.setSource("OCR");
                entity.setImagePath(imagePath);
                entity.setPurchasedAt(LocalDate.now());
                entity.setCreatedAt(LocalDateTime.now());

                saved.add(new LottoTicketDto(ticketRepo.save(entity)));
            }

            if (saved.isEmpty()) {
                return LottoTicketUploadResponse.fail("인식된 게임의 번호 형식이 올바르지 않습니다.");
            }
            return LottoTicketUploadResponse.ok(saved);

        } catch (Exception e) {
            log.error("로또 티켓 OCR 처리 실패", e);
            return LottoTicketUploadResponse.fail("처리 실패: " + e.getMessage());
        }
    }

    private int[] toNumbers(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.size() != 6) return null;
        int[] nums = new int[6];
        for (int i = 0; i < 6; i++) {
            int v = arr.get(i).asInt();
            if (v < 1 || v > 45) return null;
            nums[i] = v;
        }
        return nums;
    }

    private String saveToNas(MultipartFile file, Integer round) throws IOException {
        String roundDir = round != null ? String.valueOf(round) : "unknown";
        Path dir = Paths.get(nasPath, roundDir);
        Files.createDirectories(dir);

        String ts = LocalDateTime.now().format(FILE_TS);
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "ticket.jpg";
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String filename = ts + "_" + safeName;

        Path target = dir.resolve(filename);
        Files.write(target, file.getBytes());
        return target.toString();
    }

    private String callClaude(String base64Image, String mediaType) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-5",
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of(
                                                "type", "image",
                                                "source", Map.of(
                                                        "type", "base64",
                                                        "media_type", mediaType,
                                                        "data", base64Image
                                                )
                                        ),
                                        Map.of(
                                                "type", "text",
                                                "text", PROMPT
                                        )
                                )
                        )
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(ANTHROPIC_URL, request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
            return (String) content.get(0).get("text");
        }
        throw new RuntimeException("Claude API 호출 실패: " + response.getStatusCode());
    }

    private JsonNode parseResponse(String rawText) throws Exception {
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-z]*\\n?", "").replaceAll("```", "").trim();
        }
        return objectMapper.readTree(cleaned);
    }
}

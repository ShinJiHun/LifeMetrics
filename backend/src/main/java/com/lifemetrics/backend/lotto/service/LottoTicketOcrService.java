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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final DateTimeFormatter ISSUED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String PROMPT = """
            이 파일은 한국 로또 6/45 용지(구매 영수증)입니다. 사진일 수도, PDF일 수도 있습니다.
            다음 정보를 JSON으로 반환해주세요:

            - round: 용지에 인쇄된 추첨 회차 번호 (정수, 못 찾으면 null)
            - issuedAt: 용지에 인쇄된 발행일시. "yyyy-MM-dd HH:mm:ss" 형식 문자열로 반환 (초 단위가 없으면 00으로 채움).
              못 찾으면 null.
            - games: 용지에 있는 각 게임(A, B, C, D, E 중 존재하는 것만)의 배열.
              각 원소는 다음 형식입니다:
              { "label": "A", "numbers": [정수 6개, 오름차순 아님 인쇄된 순서 그대로] }

            자동/수동 여부는 무시하고 번호만 정확히 읽어주세요.
            번호는 1~45 범위의 정수여야 합니다.
            JSON만 반환하고 다른 텍스트는 포함하지 마세요. 예:
            {"round": 1234, "issuedAt": "2024-04-13 20:15:32", "games": [{"label":"A","numbers":[3,11,19,22,34,41]}]}
            """;

    public LottoTicketUploadResponse upload(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String base64File = Base64.getEncoder().encodeToString(fileBytes);
            boolean isPdf = isPdf(file);
            String mediaType = isPdf ? "application/pdf"
                    : (file.getContentType() != null ? file.getContentType() : "image/jpeg");

            String jsonResponse = callClaude(base64File, mediaType, isPdf);
            log.info("Claude 로또 OCR 응답: {}", jsonResponse);
            JsonNode data = parseResponse(jsonResponse);

            Integer round = data.hasNonNull("round") ? data.get("round").asInt() : null;
            LocalDateTime issuedAt = parseIssuedAt(data);
            JsonNode gamesNode = data.get("games");
            if (gamesNode == null || !gamesNode.isArray() || gamesNode.isEmpty()) {
                return LottoTicketUploadResponse.fail("용지에서 게임 번호를 인식하지 못했습니다.");
            }

            List<int[]> games = new ArrayList<>();
            for (JsonNode game : gamesNode) {
                int[] numbers = toNumbers(game.get("numbers"));
                if (numbers != null) games.add(numbers);
            }
            if (games.isEmpty()) {
                return LottoTicketUploadResponse.fail("인식된 게임의 번호 형식이 올바르지 않습니다.");
            }

            // 같은 회차 + 같은 발행일시로 이미 등록된 게임들 (중복 용지 판별). 발행일시를 못 읽었으면 판별 불가하므로 건너뜀.
            List<LottoTicketEntity> existing = (round != null && issuedAt != null)
                    ? ticketRepo.findByRoundAndIssuedAt(round, issuedAt)
                    : List.of();

            String imagePath = saveToNas(file, round);
            String ticketGroup = UUID.randomUUID().toString();

            List<LottoTicketDto> results = new ArrayList<>();
            int gameNo = 1;
            int duplicateCount = 0;
            for (int[] numbers : games) {
                LottoTicketEntity match = findMatchingNumbers(existing, numbers);
                if (match != null) {
                    duplicateCount++;
                    results.add(new LottoTicketDto(match, null, null, true));
                    continue;
                }

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
                entity.setIssuedAt(issuedAt);
                entity.setCreatedAt(LocalDateTime.now());

                results.add(new LottoTicketDto(ticketRepo.save(entity)));
            }

            if (duplicateCount == games.size()) {
                return LottoTicketUploadResponse.duplicate(results);
            }
            return LottoTicketUploadResponse.ok(results, duplicateCount);

        } catch (Exception e) {
            log.error("로또 티켓 OCR 처리 실패", e);
            return LottoTicketUploadResponse.fail("처리 실패: " + e.getMessage());
        }
    }

    /** 회차+발행일시가 같은 기존 게임들 중, 번호 구성(순서 무관)까지 같은 게 있으면 그 엔티티를 반환한다. */
    private LottoTicketEntity findMatchingNumbers(List<LottoTicketEntity> candidates, int[] numbers) {
        Set<Integer> target = toSet(numbers);
        for (LottoTicketEntity candidate : candidates) {
            if (toSet(candidate.numbers()).equals(target)) {
                return candidate;
            }
        }
        return null;
    }

    private Set<Integer> toSet(int[] numbers) {
        return java.util.Arrays.stream(numbers).boxed().collect(Collectors.toSet());
    }

    private LocalDateTime parseIssuedAt(JsonNode data) {
        if (!data.hasNonNull("issuedAt")) return null;
        String raw = data.get("issuedAt").asText().trim();
        if (raw.isEmpty()) return null;
        try {
            return LocalDateTime.parse(raw, ISSUED_AT_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("로또 용지 발행일시 파싱 실패: {}", raw);
            return null;
        }
    }

    private boolean isPdf(MultipartFile file) {
        if ("application/pdf".equalsIgnoreCase(file.getContentType())) return true;
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".pdf");
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

    private String callClaude(String base64File, String mediaType, boolean isPdf) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> fileBlock = Map.of(
                "type", isPdf ? "document" : "image",
                "source", Map.of(
                        "type", "base64",
                        "media_type", mediaType,
                        "data", base64File
                )
        );

        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-5",
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        fileBlock,
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

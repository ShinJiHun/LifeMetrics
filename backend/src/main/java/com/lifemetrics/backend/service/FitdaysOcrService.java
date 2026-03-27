package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitdaysOcrService {

    private final UserBodyRecordRepository bodyRecordRepo;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";

    private static final String PROMPT = """
            이 FitDays 체성분 결과 화면에서 다음 정보를 추출해서 JSON으로 반환해주세요:

            - record_date: 화면 상단의 날짜 (YYYY-MM-DD 형식, 예: 2026/03/25 → 2026-03-25)
            - weight: 체중 (kg)
            - bmi: BMI
            - body_fat_percentage: 체지방률 (%)
            - body_fat_mass: 체지방량 (kg)
            - skeletal_muscle_mass: 골격근량 (kg)
            - total_body_water: 체수분 (kg)
            - bone_mass: 골질량 (kg)
            - protein: 체단백질 (kg)
            - mineral: 무기질 (kg)
            - visceral_fat_index: 내장 지방지수 (숫자)
            - basal_metabolic_rate: 기초대사량 (kcal, 숫자만)

            숫자만 추출하고 단위는 제외하세요.
            없는 항목은 null로 반환하세요.
            JSON만 반환하고 다른 텍스트는 포함하지 마세요.
            """;

    public Map<String, Object> processImage(MultipartFile file, Long userId) {
        try {
            // 1. 이미지 base64 변환
            byte[] imageBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mediaType = file.getContentType() != null
                    ? file.getContentType() : "image/jpeg";

            // 2. Claude API 호출
            String jsonResponse = callClaude(base64Image, mediaType);
            log.info("Claude OCR 응답: {}", jsonResponse);

            // 3. JSON 파싱
            JsonNode data = parseResponse(jsonResponse);

            // 4. DB 저장
            UserBodyRecord record = saveToDb(data, file.getOriginalFilename(), userId);

            return Map.of(
                    "success", true,
                    "message", "FitDays 데이터가 저장되었습니다.",
                    "recordDate", record.getRecordDate().toString(),
                    "weight", record.getWeight() != null ? record.getWeight() : 0
            );

        } catch (Exception e) {
            log.error("FitDays OCR 처리 실패", e);
            return Map.of(
                    "success", false,
                    "message", "처리 실패: " + e.getMessage()
            );
        }
    }

    private String callClaude(String base64Image, String mediaType) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-4-20250514",
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
            List<Map<String, Object>> content =
                    (List<Map<String, Object>>) response.getBody().get("content");
            return (String) content.get(0).get("text");
        }
        throw new RuntimeException("Claude API 호출 실패: " + response.getStatusCode());
    }

    private JsonNode parseResponse(String rawText) throws Exception {
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-z]*\\n?", "").replaceAll("```", "").trim();
        }
        JsonNode node = objectMapper.readTree(cleaned);
        return node.isArray() ? node.get(0) : node;
    }

    private UserBodyRecord saveToDb(JsonNode data, String filename, Long userId) {
        String dateStr = getStr(data, "record_date");
        LocalDate recordDate = dateStr != null
                ? LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : LocalDate.now();

        // 기존 레코드 조회 (upsert)
        UserBodyRecord record = bodyRecordRepo
                .findByUserIdAndRecordDate(userId, recordDate)
                .orElse(new UserBodyRecord());

        record.setUserId(userId);
        record.setRecordDate(recordDate);
        record.setMeasurementType(MeasurementType.FITDAYS);

        record.setWeight(getDouble(data, "weight"));
        record.setBmi(getDouble(data, "bmi"));
        record.setBodyFatPercentage(getDouble(data, "body_fat_percentage"));
        record.setBodyFatMass(getDouble(data, "body_fat_mass"));
        record.setSkeletalMuscleMass(getDouble(data, "skeletal_muscle_mass"));

        // 엔티티 필드명 맞춤
        record.setBodyWater(getDouble(data, "total_body_water"));       // total_body_water → bodyWater
        record.setProteinMass(getDouble(data, "protein"));               // protein → proteinMass
        record.setMineral(getDouble(data, "mineral"));
        record.setBoneMass(getDouble(data, "bone_mass"));
        record.setBasalMetabolicRate(getDouble(data, "basal_metabolic_rate"));

        Double vfi = getDouble(data, "visceral_fat_index");
        record.setVisceralFatLevel(vfi != null ? vfi.intValue() : null);

        return bodyRecordRepo.save(record);
    }

    // ── 유틸 ─────────────────────────────────────────────────────
    private Double getDouble(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        try { return val.asDouble(); } catch (Exception e) { return null; }
    }

    private String getStr(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return val.asText();
    }
}

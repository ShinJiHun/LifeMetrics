package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BikeSpecExtractService {

    private final ObjectMapper objectMapper;

    @Value("${anthropic.api-key:}")
    private String anthropicApiKey;

    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String URL = "https://api.anthropic.com/v1/messages";

    private static final String SPEC_JSON = """
            {
              "brand": "...", "model": "...", "model_year": 2025,
              "bike_type": "ROAD/GRAVEL/MTB/TT/CX",
              "ride_category": "ENDURANCE/RACING/AERO/ALLROAD",
              "frame_material": "카본/알루미늄/티타늄/스틸",
              "frame_size": null, "weight_kg": null, "wheel_size": "700C",
              "groupset": "구동계 풀네임", "shifting_type": "DI2/AXS/EPS/MECHANICAL", "speeds": 12,
              "chainring_outer": 50, "chainring_inner": 34, "cassette_label": "11-34T",
              "sources": ["참고 URL"],
              "confidence_notes": "불확실하거나 빌드/연식별로 다른 필드를 한 줄로 설명"
            }""";

    // ── 이미지(스펙시트) → 사양 추출 (비전) ──────────────────────
    public JsonNode extractFromImage(MultipartFile image) {
        try {
            String b64 = Base64.getEncoder().encodeToString(image.getBytes());
            String mediaType = image.getContentType() != null ? image.getContentType() : "image/png";
            String prompt = "자전거 스펙시트 이미지입니다. 보이는 정보만 추출, 없으면 null, 추측 금지. "
                    + "크랭크/카세트는 톱니수 명시된 경우만.\n반드시 아래 JSON만 출력(설명/마크다운 금지):\n" + SPEC_JSON;

            Map<String, Object> img = Map.of("type", "image",
                    "source", Map.of("type", "base64", "media_type", mediaType, "data", b64));
            Map<String, Object> txt = Map.of("type", "text", "text", prompt);
            Map<String, Object> body = Map.of("model", MODEL, "max_tokens", 1024,
                    "messages", List.of(Map.of("role", "user", "content", List.of(img, txt))));

            return callAndParse(body);
        } catch (Exception e) {
            throw new RuntimeException("이미지 스펙 추출 실패: " + e.getMessage(), e);
        }
    }

    // ── 모델명 → 웹검색으로 사양 추출 (web_search 도구) ──────────
    public JsonNode searchSpec(String model) {
        try {
            String prompt = "\"" + model + "\" 자전거의 사양을 웹에서 검색해 정리하세요. "
                    + "공식 제조사/판매처 우선, 확실하지 않으면 null, 추측 금지. "
                    + "빌드/연식별로 다르면 confidence_notes에 명시.\n"
                    + "반드시 아래 JSON만 출력(설명/마크다운 금지):\n" + SPEC_JSON;

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "max_tokens", 2000,
                    "tools", List.of(Map.of("type", "web_search_20250305", "name", "web_search", "max_uses", 5)),
                    "messages", List.of(Map.of("role", "user", "content", prompt)));

            return callAndParse(body);
        } catch (Exception e) {
            throw new RuntimeException("모델 검색 실패: " + e.getMessage(), e);
        }
    }

    // ── 공통 호출 + JSON 파싱 ────────────────────────────────────
    private JsonNode callAndParse(Map<String, Object> body) throws Exception {
        if (anthropicApiKey == null || anthropicApiKey.isEmpty())
            throw new RuntimeException("anthropic.api-key 미설정");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("x-api-key", anthropicApiKey);
        h.set("anthropic-version", "2023-06-01");

        ResponseEntity<String> res = new RestTemplate()
                .postForEntity(URL, new HttpEntity<>(body, h), String.class);

        // content 배열에서 text 블록만 이어붙임 (web_search_tool_result 등은 무시)
        JsonNode content = objectMapper.readTree(res.getBody()).get("content");
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content)
            if ("text".equals(block.path("type").asText()))
                sb.append(block.path("text").asText());

        String t = sb.toString();
        int s = t.indexOf('{'), e = t.lastIndexOf('}');   // 앞뒤 설명 제거, JSON만
        if (s >= 0 && e > s) t = t.substring(s, e + 1);
        return objectMapper.readTree(t);
    }
}
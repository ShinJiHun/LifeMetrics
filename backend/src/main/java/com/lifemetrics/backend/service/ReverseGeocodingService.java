// service/ReverseGeocodingService.java
package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 좌표 -> "동/구, 시" 형태의 한글 지명으로 변환합니다.
 * <p>
 * Mapbox Geocoding API 사용 (이미 VITE_MAPBOX_TOKEN 사용 중이니 동일 토큰 재활용).
 * 무료 한도: 월 100,000회.
 * <p>
 * 캐싱을 권장합니다 — Activity 1개당 1회만 호출하고 결과를 DB에 저장하세요.
 */
@Slf4j
@Service
public class ReverseGeocodingService {

    @Value("${mapbox.token:}")
    private String mapboxToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 좌표를 "천호동, 서울" 형식의 한글 지명으로 변환.
     * 실패 시 null 반환 (DTO 빌드 시 무시됨).
     */
    public String reverseGeocode(Double lat, Double lon) {
        if (lat == null || lon == null) return null;
        if (mapboxToken == null || mapboxToken.isBlank()) {
            log.warn("Mapbox token not configured, skipping reverse geocoding");
            return null;
        }

        try {
            String url = String.format(
                    "https://api.mapbox.com/geocoding/v5/mapbox.places/%f,%f.json"
                            + "?language=ko&types=neighborhood,locality,place&access_token=%s",
                    lon, lat, mapboxToken
            );

            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = mapper.readTree(body);
            JsonNode features = root.path("features");
            if (!features.isArray() || features.isEmpty()) return null;

            // neighborhood (동) + place (시) 조합
            String neighborhood = null;
            String place = null;

            for (JsonNode f : features) {
                JsonNode placeTypes = f.path("place_type");
                if (!placeTypes.isArray()) continue;
                String type = placeTypes.get(0).asText();
                String text = f.path("text_ko").asText(f.path("text").asText());

                if ("neighborhood".equals(type) && neighborhood == null) {
                    neighborhood = text;
                } else if (("place".equals(type) || "locality".equals(type)) && place == null) {
                    place = text;
                }
            }

            if (neighborhood != null && place != null) {
                return neighborhood + ", " + place;
            } else if (neighborhood != null) {
                return neighborhood;
            } else if (place != null) {
                return place;
            }
        } catch (Exception e) {
            log.warn("Reverse geocoding failed for ({}, {}): {}", lat, lon, e.getMessage());
        }

        return null;
    }
}

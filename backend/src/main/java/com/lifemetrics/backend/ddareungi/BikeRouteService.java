package com.lifemetrics.backend.ddareungi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.Optional;

/**
 * 두 지점 사이 자전거 경로 추정 (OSRM bike 프로파일).
 * 기본값은 FOSSGIS 공개 서버(routing.openstreetmap.de)로, 가끔 쓰는 정도는 괜찮지만
 * 대량 호출은 하지 않는다. 응답 geometry 는 Google encoded polyline(precision 5)이라
 * activity_core.polyline 에 그대로 넣을 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BikeRouteService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ddareungi.route-url:https://routing.openstreetmap.de/routed-bike}")
    private String routeBaseUrl;

    /** @param polyline encoded polyline, @param distanceM 경로 길이(m) */
    public record Route(String polyline, double distanceM) {}

    public Optional<Route> route(double fromLat, double fromLon, double toLat, double toLon) {
        String url = String.format(Locale.ROOT,
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline",
                routeBaseUrl, fromLon, fromLat, toLon, toLat);
        try {
            JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            if (!"Ok".equals(root.path("code").asText())) {
                log.warn("자전거 경로 탐색 실패: {}", root.path("code").asText());
                return Optional.empty();
            }
            JsonNode r = root.path("routes").path(0);
            return Optional.of(new Route(r.path("geometry").asText(), r.path("distance").asDouble()));
        } catch (Exception e) {
            log.warn("자전거 경로 탐색 오류: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

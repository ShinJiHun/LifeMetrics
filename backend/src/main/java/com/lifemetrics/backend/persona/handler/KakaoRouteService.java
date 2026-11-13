package com.lifemetrics.backend.persona.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.util.PolylineEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 핸들 기록용 주소 → 좌표, 좌표 → 자동차 경로.
 * <p>
 * 주소 검색은 카카오 로컬(주소 → 키워드 순), 경로는 카카오모빌리티 길찾기 priority=TIME(가장 빠른 길,
 * 호출 시점 교통 반영). 카카오가 실패하면 OSRM 자동차 프로파일, 그것도 안 되면 직선.
 * 키는 data_pipeline/.env 의 KAKAO_REST_API_KEY (컨테이너 env 로 들어와 있음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoRouteService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${KAKAO_REST_API_KEY:}")
    private String kakaoKey;

    @Value("${handler.trip.osrm-url:https://routing.openstreetmap.de/routed-car}")
    private String osrmUrl;

    /** @param source address / keyword */
    public record Place(String query, String address, String placeName, double lat, double lon, String source) {}

    /** @param source kakao / osrm / straight */
    public record Route(String polyline, int distanceM, Integer durationS, String source) {}

    // ── 지오코딩 ─────────────────────────────

    /** 주소가 있으면 주소 검색 → 주소로 키워드 검색 → 장소명 키워드 검색 순으로 찾는다. */
    public Optional<Place> geocode(String address, String name) {
        if (kakaoKey == null || kakaoKey.isBlank()) {
            log.warn("KAKAO_REST_API_KEY 없음 → 지오코딩 불가");
            return Optional.empty();
        }
        if (notBlank(address)) {
            Optional<Place> p = searchAddress(address);
            if (p.isEmpty()) p = searchKeyword(address);
            if (p.isPresent()) return p;
        }
        if (notBlank(name)) {
            // 동네 이름을 붙여 검색해야 다른 지역의 같은 이름을 피한다 (예: "구리시 인창동 유수지주차장")
            String region = regionOf(address);
            if (region != null) {
                Optional<Place> p = searchKeyword(region + " " + name);
                if (p.isPresent()) return p;
            }
            return searchKeyword(name);
        }
        return Optional.empty();
    }

    /** "경기 구리시 인창동 239-3" → "구리시 인창동" (시·군·구 + 읍·면·동·로·길). 못 뽑으면 null. */
    static String regionOf(String address) {
        if (address == null) return null;
        List<String> parts = new ArrayList<>();
        for (String t : address.trim().split("\\s+")) {
            if (t.matches(".+(시|군|구)") && !t.matches(".*(특별시|광역시|자치시)") || t.matches(".+(읍|면|동|가|리|로|길)")) {
                parts.add(t);
            } else if (!parts.isEmpty()) {
                break;
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private Optional<Place> searchAddress(String q) {
        JsonNode doc = kakaoGet("https://dapi.kakao.com/v2/local/search/address.json", q);
        if (doc == null) return Optional.empty();
        String addr = doc.path("road_address").path("address_name").asText("");
        if (addr.isBlank()) addr = doc.path("address_name").asText(q);
        return Optional.of(new Place(q, addr, null, doc.path("y").asDouble(), doc.path("x").asDouble(), "address"));
    }

    private Optional<Place> searchKeyword(String q) {
        JsonNode doc = kakaoGet("https://dapi.kakao.com/v2/local/search/keyword.json", q);
        if (doc == null) return Optional.empty();
        String addr = doc.path("road_address_name").asText("");
        if (addr.isBlank()) addr = doc.path("address_name").asText("");
        return Optional.of(new Place(q, addr, doc.path("place_name").asText(null),
                doc.path("y").asDouble(), doc.path("x").asDouble(), "keyword"));
    }

    private JsonNode kakaoGet(String base, String query) {
        try {
            String url = UriComponentsBuilder.fromUriString(base)
                    .queryParam("query", query.trim())
                    .queryParam("size", 1)
                    .build().encode().toUriString();
            String body = restTemplate.exchange(java.net.URI.create(url), HttpMethod.GET, kakaoEntity(), String.class).getBody();
            JsonNode d = objectMapper.readTree(body).path("documents").path(0);
            return d.isMissingNode() ? null : d;
        } catch (Exception e) {
            log.warn("카카오 검색 실패 {}: {}", query, e.getMessage());
            return null;
        }
    }

    // ── 경로 ─────────────────────────────

    public Route route(double fromLat, double fromLon, double toLat, double toLon) {
        return kakaoRoute(fromLat, fromLon, toLat, toLon)
                .or(() -> osrmRoute(fromLat, fromLon, toLat, toLon))
                .orElseGet(() -> new Route(
                        PolylineEncoder.encodeLatLng(List.of(new double[]{fromLat, fromLon}, new double[]{toLat, toLon})),
                        (int) Math.round(haversineM(fromLat, fromLon, toLat, toLon)), null, "straight"));
    }

    private Optional<Route> kakaoRoute(double fromLat, double fromLon, double toLat, double toLon) {
        if (kakaoKey == null || kakaoKey.isBlank()) return Optional.empty();
        String url = String.format(Locale.ROOT,
                "https://apis-navi.kakaomobility.com/v1/directions?origin=%.7f,%.7f&destination=%.7f,%.7f&priority=TIME",
                fromLon, fromLat, toLon, toLat);
        try {
            String body = restTemplate.exchange(java.net.URI.create(url), HttpMethod.GET, kakaoEntity(), String.class).getBody();
            JsonNode r = objectMapper.readTree(body).path("routes").path(0);
            if (r.path("result_code").asInt(-1) != 0) {
                log.warn("카카오 길찾기 실패: {}", r.path("result_msg").asText());
                return Optional.empty();
            }
            List<double[]> pts = new ArrayList<>();
            for (JsonNode sec : r.path("sections")) {
                for (JsonNode road : sec.path("roads")) {
                    JsonNode v = road.path("vertexes");
                    for (int i = 0; i + 1 < v.size(); i += 2) {
                        double lon = v.get(i).asDouble(), lat = v.get(i + 1).asDouble();
                        double[] last = pts.isEmpty() ? null : pts.get(pts.size() - 1);
                        if (last == null || last[0] != lat || last[1] != lon) pts.add(new double[]{lat, lon});
                    }
                }
            }
            if (pts.size() < 2) return Optional.empty();
            JsonNode s = r.path("summary");
            return Optional.of(new Route(PolylineEncoder.encodeLatLng(pts),
                    s.path("distance").asInt(), s.path("duration").asInt(), "kakao"));
        } catch (Exception e) {
            log.warn("카카오 길찾기 오류: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Route> osrmRoute(double fromLat, double fromLon, double toLat, double toLon) {
        String url = String.format(Locale.ROOT,
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline",
                osrmUrl, fromLon, fromLat, toLon, toLat);
        try {
            JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            if (!"Ok".equals(root.path("code").asText())) return Optional.empty();
            JsonNode r = root.path("routes").path(0);
            return Optional.of(new Route(r.path("geometry").asText(),
                    (int) Math.round(r.path("distance").asDouble()),
                    (int) Math.round(r.path("duration").asDouble()), "osrm"));
        } catch (Exception e) {
            log.warn("OSRM 자동차 경로 오류: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private HttpEntity<Void> kakaoEntity() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoKey);
        return new HttpEntity<>(h);
    }

    static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

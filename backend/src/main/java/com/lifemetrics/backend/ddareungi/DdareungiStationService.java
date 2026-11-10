package com.lifemetrics.backend.ddareungi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 따릉이 대여소 이름 → 좌표 조회.
 * 1) ddareungi_station 테이블(서울 열린데이터 bikeList 를 동기화한 캐시)에서 번호/이름으로 찾고
 * 2) 못 찾으면 Mapbox 지오코딩으로 "이름 + 서울"을 검색한다(정확도가 떨어지므로 보조 수단).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DdareungiStationService {

    private final DdareungiStationRepository stationRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /** 서울 열린데이터광장 인증키 (data.seoul.go.kr 에서 무료 발급). */
    @Value("${seoul.openapi-key:}")
    private String seoulApiKey;

    @Value("${mapbox.token:}")
    private String mapboxToken;

    private static final String BIKE_LIST_URL = "http://openapi.seoul.go.kr:8088/%s/json/bikeList/%d/%d/";
    private static final int PAGE = 1000;
    /** "102. 망원역 1번출구 앞" → 번호 102, 이름 "망원역 1번출구 앞" */
    private static final Pattern NO_PREFIX = Pattern.compile("^\\s*(\\d+)\\s*[.)]?\\s*(.*)$");

    public record StationPoint(String name, double lat, double lon, String source) {}

    /** 서울시 대여소 전체를 받아 ddareungi_station 에 upsert 한다. @return 저장한 대여소 수 */
    @Transactional
    public int syncStations() {
        if (seoulApiKey == null || seoulApiKey.isBlank()) {
            throw new IllegalStateException("seoul.openapi-key 가 설정되지 않았습니다 (data.seoul.go.kr 인증키 필요)");
        }
        List<DdareungiStation> all = new ArrayList<>();
        for (int start = 1; ; start += PAGE) {
            String url = String.format(BIKE_LIST_URL, seoulApiKey, start, start + PAGE - 1);
            JsonNode root;
            try {
                root = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            } catch (Exception e) {
                throw new IllegalStateException("서울시 bikeList 호출 실패: " + e.getMessage(), e);
            }
            JsonNode status = root.path("rentBikeStatus");
            if (status.isMissingNode()) {
                // 키 오류 등은 최상위 RESULT 로 온다
                String msg = root.path("RESULT").path("MESSAGE").asText(root.toString());
                if (start == 1) throw new IllegalStateException("서울시 bikeList 오류: " + msg);
                break; // 마지막 페이지를 넘긴 경우(INFO-200 데이터 없음)
            }
            JsonNode rows = status.path("row");
            for (JsonNode r : rows) {
                DdareungiStation s = new DdareungiStation();
                s.setStationId(r.path("stationId").asText());
                String[] noAndName = splitNo(r.path("stationName").asText());
                s.setStationNo(noAndName[0]);
                s.setStationName(noAndName[1]);
                s.setLat(r.path("stationLatitude").asDouble());
                s.setLon(r.path("stationLongitude").asDouble());
                if (s.getLat() != 0 && s.getLon() != 0) all.add(s);
            }
            if (rows.size() < PAGE) break;
        }
        stationRepository.saveAll(all);
        log.info("따릉이 대여소 {}곳 동기화", all.size());
        return all.size();
    }

    /** 대여소 이름(번호가 붙어 있어도 됨)과 별도 번호로 좌표를 찾는다. */
    public Optional<StationPoint> find(String rawName, String stationNo) {
        String[] noAndName = splitNo(rawName == null ? "" : rawName);
        String no = stationNo != null && !stationNo.isBlank() ? stationNo.trim() : noAndName[0];
        String name = noAndName[1];

        // 1) 번호 일치 (같은 번호가 여럿이면 이름이 비슷한 쪽)
        if (no != null) {
            List<DdareungiStation> byNo = stationRepository.findByStationNo(no);
            if (byNo.size() == 1) return Optional.of(toPoint(byNo.get(0)));
            if (byNo.size() > 1 && !name.isBlank()) {
                return byNo.stream().min(Comparator.comparingInt(s -> distance(norm(s.getStationName()), norm(name))))
                        .map(this::toPoint);
            }
        }
        if (name.isBlank()) return Optional.empty();

        // 2) 이름 정확히 일치
        List<DdareungiStation> exact = stationRepository.findByStationName(name);
        if (!exact.isEmpty()) return Optional.of(toPoint(exact.get(0)));

        // 3) 공백을 무시하고 가장 비슷한 이름 (앞 두 글자로 후보를 좁힌다)
        String key = norm(name);
        List<DdareungiStation> candidates = stationRepository.findByStationNameContaining(
                name.substring(0, Math.min(2, name.length())));
        Optional<DdareungiStation> best = candidates.stream()
                .min(Comparator.comparingInt(s -> distance(norm(s.getStationName()), key)));
        if (best.isPresent() && distance(norm(best.get().getStationName()), key) <= Math.max(2, key.length() / 4)) {
            return best.map(this::toPoint);
        }

        // 4) Mapbox 지오코딩 (보조)
        return geocode(name);
    }

    private Optional<StationPoint> geocode(String name) {
        if (mapboxToken == null || mapboxToken.isBlank()) return Optional.empty();
        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://api.mapbox.com/geocoding/v5/mapbox.places/{q}.json")
                    .queryParam("access_token", mapboxToken)
                    .queryParam("country", "kr")
                    .queryParam("language", "ko")
                    .queryParam("limit", 1)
                    // 서울 영역으로 제한
                    .queryParam("bbox", "126.76,37.41,127.19,37.72")
                    .buildAndExpand(name.replaceAll("\\s*(앞|뒤|옆|건너편)$", ""))
                    .toUriString();
            JsonNode f = objectMapper.readTree(restTemplate.getForObject(url, String.class))
                    .path("features").path(0);
            if (f.isMissingNode()) return Optional.empty();
            JsonNode c = f.path("center");
            return Optional.of(new StationPoint(name, c.get(1).asDouble(), c.get(0).asDouble(), "geocode"));
        } catch (Exception e) {
            log.warn("따릉이 대여소 지오코딩 실패 {}: {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    private StationPoint toPoint(DdareungiStation s) {
        return new StationPoint(s.getStationName(), s.getLat(), s.getLon(), "station");
    }

    static String[] splitNo(String raw) {
        Matcher m = NO_PREFIX.matcher(raw);
        if (m.matches() && !m.group(2).isBlank()) return new String[]{m.group(1), m.group(2).trim()};
        return new String[]{null, raw.trim()};
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("[\\s.·()\\-]", "");
    }

    /** 레벤슈타인 거리 */
    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}

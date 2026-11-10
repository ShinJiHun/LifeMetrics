package com.lifemetrics.backend.ddareungi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import com.lifemetrics.backend.service.ClaudeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 따릉이 이용내역 캡처 → activity_core (ride_type = DDAREUNGI).
 *
 * 캡처에는 GPS 기록이 없으므로 경로는 "대여소 좌표 → 반납소 좌표" 자전거 길찾기 결과로 추정한다.
 * activity_point 는 만들지 않는다(가짜 포인트가 속도/구간 분석에 섞이지 않도록). 지도는 polyline 으로 그린다.
 * 칼로리는 캡처에 나온 값을 우선 쓰고, 없으면 체중 × MET × 시간으로 계산한다.
 */
@Slf4j
@Service
public class DdareungiImportService {

    public static final String RIDE_TYPE = "DDAREUNGI";
    /** activity_core.id 는 파서가 Strava id 나 yyyyMMddHHmmss 를 쓰므로 9 를 앞에 붙여 따릉이 전용 대역을 쓴다. */
    private static final String ID_PREFIX = "9";
    private static final DateTimeFormatter ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final double DEFAULT_WEIGHT_KG = 70.0;

    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;
    private final DdareungiStationService stationService;
    private final BikeRouteService routeService;
    private final UserBodyRecordRepository bodyRecordRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public DdareungiImportService(ClaudeClient claudeClient, ObjectMapper objectMapper,
                                  DdareungiStationService stationService, BikeRouteService routeService,
                                  UserBodyRecordRepository bodyRecordRepository,
                                  @Qualifier("ridingDataSource") DataSource dataSource) {
        this.claudeClient = claudeClient;
        this.objectMapper = objectMapper;
        this.stationService = stationService;
        this.routeService = routeService;
        this.bodyRecordRepository = bodyRecordRepository;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    private static final String SYSTEM = "너는 서울 공공자전거 따릉이 앱 화면에서 이용내역을 정확히 읽어 JSON 으로만 답한다.";

    private static final String PROMPT = """
            이 따릉이 이용내역 화면에 있는 대여 건을 모두 찾아 JSON 배열로 반환해주세요. 오늘 날짜는 %s 입니다.

            각 원소:
            - rent_station: 대여소 이름 (화면에 쓰인 그대로, 번호가 있으면 번호 포함. 예: "102. 망원역 1번출구 앞")
            - return_station: 반납소 이름 (같은 규칙)
            - rent_time: 대여 일시 "YYYY-MM-DD HH:mm:ss" (초가 없으면 :00)
            - return_time: 반납 일시 "YYYY-MM-DD HH:mm:ss"
            - duration_min: 이용 시간(분, 숫자)
            - distance_km: 이동 거리(km, 숫자. m 로 표시돼 있으면 km 로 환산)
            - calories_kcal: 운동량/칼로리(kcal, 숫자)
            - carbon_kg: 탄소절감량(kg, 숫자)

            규칙:
            - 연도가 화면에 없으면 오늘 날짜 기준으로 미래가 되지 않는 가장 최근 연도를 쓰세요.
            - 반납 시각이 없고 이용 시간만 있으면 대여 시각 + 이용 시간으로 계산하세요.
            - 화면에 없는 값은 null. 단위는 빼고 숫자만.
            - 대여 건이 하나여도 배열로 반환하고, JSON 외의 텍스트는 쓰지 마세요.
            """;

    public record RideResult(
            String status,          // saved / duplicate / preview / fail
            Long activityId,
            String rentStation,
            String returnStation,
            String rentTime,
            String returnTime,
            Double distanceKm,
            Integer durationMin,
            Integer calories,
            String caloriesSource,  // screen / met
            String routeSource,     // route / straight / none
            String message) {}

    public record FileResult(String filename, String status, String message, List<RideResult> rides) {}

    public FileResult importImage(MultipartFile file, Long userId, boolean dryRun) {
        try {
            return importImage(file.getOriginalFilename(), file.getContentType(), file.getBytes(), userId, dryRun);
        } catch (java.io.IOException e) {
            return new FileResult(file.getOriginalFilename(), "fail", "파일을 읽지 못했습니다", List.of());
        }
    }

    /** 요청이 끝난 뒤(비동기 작업)에도 쓸 수 있도록 바이트로 받는 버전. */
    public FileResult importImage(String filename, String contentType, byte[] bytes, Long userId, boolean dryRun) {
        try {
            String mediaType = contentType != null ? contentType : "image/jpeg";
            if (!Set.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(mediaType)) {
                return new FileResult(filename, "fail", "지원하지 않는 이미지 형식: " + mediaType + " (jpg/png/webp)", List.of());
            }
            String raw = claudeClient.completeWithImage(SYSTEM, PROMPT.formatted(LocalDate.now()),
                    bytes, mediaType, 2048);
            if (raw == null) {
                return new FileResult(filename, "fail", "이미지 인식 실패 (Claude 응답 없음)", List.of());
            }
            log.info("따릉이 OCR 응답 {}: {}", filename, raw);
            JsonNode arr = parseJson(raw);
            if (!arr.isArray() || arr.isEmpty()) {
                return new FileResult(filename, "fail", "이용내역을 찾지 못했습니다", List.of());
            }
            List<RideResult> rides = new ArrayList<>();
            for (JsonNode n : arr) {
                rides.add(importRide(n, userId, dryRun));
            }
            boolean anyFail = rides.stream().anyMatch(r -> "fail".equals(r.status()));
            return new FileResult(filename, anyFail ? "partial" : "success", null, rides);
        } catch (Exception e) {
            log.error("따릉이 이미지 처리 실패 {}", filename, e);
            return new FileResult(filename, "fail", e.getMessage(), List.of());
        }
    }

    private RideResult importRide(JsonNode n, Long userId, boolean dryRun) {
        String rentStation = text(n, "rent_station");
        String returnStation = text(n, "return_station");
        Double distanceKm = num(n, "distance_km");
        Double durationMinD = num(n, "duration_min");
        Double screenKcal = num(n, "calories_kcal");

        LocalDateTime start = time(n, "rent_time");
        LocalDateTime end = time(n, "return_time");
        if (start == null) {
            return new RideResult("fail", null, rentStation, returnStation, null, null,
                    distanceKm, null, null, null, null, "대여 일시를 읽지 못했습니다");
        }
        if (end == null && durationMinD != null) end = start.plusSeconds(Math.round(durationMinD * 60));
        int elapsedSec = end != null ? (int) Duration.between(start, end).getSeconds()
                : durationMinD != null ? (int) Math.round(durationMinD * 60) : 0;
        if (elapsedSec <= 0) {
            return new RideResult("fail", null, rentStation, returnStation, start.toString(), null,
                    distanceKm, null, null, null, null, "이용 시간을 알 수 없습니다");
        }
        if (end == null) end = start.plusSeconds(elapsedSec);

        long id = Long.parseLong(ID_PREFIX + start.format(ID_FMT));
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM activity_core WHERE id = :id",
                Map.of("id", id), Integer.class);
        if (exists != null && exists > 0) {
            return new RideResult("duplicate", id, rentStation, returnStation, start.toString(), end.toString(),
                    distanceKm, elapsedSec / 60, null, null, null, "이미 등록된 기록");
        }

        // ── 추정 경로 ──
        var from = stationService.find(rentStation, null);
        var to = stationService.find(returnStation, null);
        String polyline = null;
        String routeSource = "none";
        Double routeDistanceM = null;
        List<String> notes = new ArrayList<>();
        if (from.isEmpty()) notes.add("대여소 좌표 없음: " + rentStation);
        if (to.isEmpty()) notes.add("반납소 좌표 없음: " + returnStation);
        if (from.isPresent() && to.isPresent()) {
            var a = from.get();
            var b = to.get();
            boolean sameSpot = haversineM(a.lat(), a.lon(), b.lat(), b.lon()) < 50;
            if (sameSpot) {
                notes.add("같은 대여소에 반납 → 경로 추정 불가");
            } else {
                var route = routeService.route(a.lat(), a.lon(), b.lat(), b.lon());
                if (route.isPresent()) {
                    polyline = route.get().polyline();
                    routeDistanceM = route.get().distanceM();
                    routeSource = "route";
                } else {
                    polyline = com.lifemetrics.backend.util.PolylineEncoder.encodeLatLng(
                            List.of(new double[]{a.lat(), a.lon()}, new double[]{b.lat(), b.lon()}));
                    routeSource = "straight";
                    notes.add("길찾기 실패 → 직선으로 표시");
                }
            }
            if ("geocode".equals(a.source()) || "geocode".equals(b.source())) {
                notes.add("대여소 위치를 지명 검색으로 추정 (대여소 목록 동기화 필요)");
            }
        }

        // 거리: 화면 값 우선(따릉이 단말 기록), 없으면 추정 경로 길이
        Double distanceM = distanceKm != null ? distanceKm * 1000 : routeDistanceM;
        Double avgSpeed = distanceM != null ? distanceM / elapsedSec * 3.6 : null;

        // 칼로리: 화면 값 우선, 없으면 MET
        int calories;
        String caloriesSource;
        if (screenKcal != null && screenKcal > 0) {
            calories = (int) Math.round(screenKcal);
            caloriesSource = "screen";
        } else {
            calories = (int) Math.round(met(avgSpeed) * weightKg(userId) * elapsedSec / 3600.0);
            caloriesSource = "met";
        }

        String rentName = from.map(DdareungiStationService.StationPoint::name)
                .orElse(DdareungiStationService.splitNo(nz(rentStation))[1]);
        String returnName = to.map(DdareungiStationService.StationPoint::name)
                .orElse(DdareungiStationService.splitNo(nz(returnStation))[1]);
        String title = "따릉이 " + rentName + " → " + returnName;
        String message = notes.isEmpty() ? null : String.join(" / ", notes);

        if (dryRun) {
            return new RideResult("preview", id, rentStation, returnStation, start.toString(), end.toString(),
                    distanceM != null ? round2(distanceM / 1000) : null, elapsedSec / 60, calories,
                    caloriesSource, routeSource, message);
        }

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("filename", "ddareungi_" + start.format(ID_FMT))
                .addValue("name", truncate(title, 250))
                .addValue("start", start)
                .addValue("end", end)
                .addValue("dist", distanceM)
                .addValue("sec", elapsedSec)
                .addValue("avg", avgSpeed)
                .addValue("cal", calories)
                .addValue("sLat", from.map(DdareungiStationService.StationPoint::lat).orElse(null))
                .addValue("sLon", from.map(DdareungiStationService.StationPoint::lon).orElse(null))
                .addValue("eLat", to.map(DdareungiStationService.StationPoint::lat).orElse(null))
                .addValue("eLon", to.map(DdareungiStationService.StationPoint::lon).orElse(null))
                .addValue("poly", polyline)
                .addValue("loc", truncate(rentName, 250))
                .addValue("rideType", RIDE_TYPE);
        jdbc.update("""
                INSERT INTO activity_core
                  (id, user_id, filename, name, start_time, end_time, total_distance, moving_time, elapsed_time,
                   avg_speed, calories, start_lat, start_lon, end_lat, end_lon, polyline, location_name, ride_type)
                VALUES
                  (:id, :userId, :filename, :name, :start, :end, :dist, :sec, :sec,
                   :avg, :cal, :sLat, :sLon, :eLat, :eLon, :poly, :loc, :rideType)
                """, p);
        log.info("따릉이 기록 저장 id={} {} {}m {}kcal route={}", id, title, distanceM, calories, routeSource);

        return new RideResult("saved", id, rentStation, returnStation, start.toString(), end.toString(),
                distanceM != null ? round2(distanceM / 1000) : null, elapsedSec / 60, calories,
                caloriesSource, routeSource, message);
    }

    /** Compendium of Physical Activities 자전거 MET (속도 구간별). 속도를 모르면 여가용 4.0 */
    static double met(Double avgSpeedKmh) {
        if (avgSpeedKmh == null || avgSpeedKmh < 16) return 4.0;
        if (avgSpeedKmh < 19.3) return 6.8;
        if (avgSpeedKmh < 22.5) return 8.0;
        return 10.0;
    }

    private double weightKg(Long userId) {
        return bodyRecordRepository.findTopByUserIdOrderByRecordDateDesc(userId)
                .map(UserBodyRecord::getWeight)
                .filter(w -> w != null && w > 0)
                .orElse(DEFAULT_WEIGHT_KG);
    }

    // ── 유틸 ──
    private JsonNode parseJson(String raw) throws Exception {
        String s = raw.trim();
        if (s.startsWith("```")) s = s.replaceAll("```[a-z]*\\n?", "").replaceAll("```", "").trim();
        int a = s.indexOf('['), o = s.indexOf('{');
        if (a < 0 && o >= 0) {
            return objectMapper.createArrayNode().add(objectMapper.readTree(s.substring(o, s.lastIndexOf('}') + 1)));
        }
        return objectMapper.readTree(s.substring(a, s.lastIndexOf(']') + 1));
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }

    private static Double num(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.asDouble();
        try {
            return Double.parseDouble(v.asText().replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime time(JsonNode n, String f) {
        String s = text(n, f);
        if (s == null) return null;
        s = s.replace('T', ' ').replace('.', '-');
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}

package com.lifemetrics.backend.persona.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.service.ClaudeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 인간 페르소나 — 핸들러 핸들(운행) 1건 기록.
 * <p>
 * 핸들 내역 캡처 1장 → Claude 가 날짜·시각·출발지·도착지 등을 읽고 → 주소를 좌표로 →
 * 카카오 길찾기(가장 빠른 길)로 경로를 만들어 polyline 으로 저장한다. 실제 주행 GPS 가 아니라
 * "출발 → 도착 최단시간 경로" 추정이다. 캡처 원본은 정산서 폴더 아래 trips/ 에 남긴다.
 */
@Slf4j
@Service
public class HandlerTripService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;
    private final KakaoRouteService routeService;
    private final NamedParameterJdbcTemplate jdbc;

    @Value("${handler.settlement.path:/tmp/handler-settlement/}")
    private String basePath;

    public HandlerTripService(ClaudeClient claudeClient, ObjectMapper objectMapper, KakaoRouteService routeService,
                              @Qualifier("ridingDataSource") DataSource dataSource) {
        this.claudeClient = claudeClient;
        this.objectMapper = objectMapper;
        this.routeService = routeService;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    /** 캡처에서 읽은 값 + 사용자가 고친 값. 시간은 "HH:mm", 날짜는 "YYYY-MM-DD". */
    public record Draft(
            String tripDate, String startTime, String endTime,
            String serviceType, String carModel, String carNumber,
            Integer fare, Double distanceKm, Integer durationMin,
            String originName, String originAddress,
            String destName, String destAddress,
            String memo, String handleCode) {}

    public record Preview(Draft draft, KakaoRouteService.Place origin, KakaoRouteService.Place dest,
                          KakaoRouteService.Route route, Long duplicateId, List<String> warnings, String raw) {}

    public record Trip(Long id, String tripDate, String startTime, String endTime,
                       String serviceType, String carModel, String carNumber,
                       Integer fare, Double screenDistanceKm, Integer durationMin,
                       String originName, String originAddress, Double originLat, Double originLon,
                       String destName, String destAddress, Double destLat, Double destLon,
                       String routeSource, Integer routeDistanceM, Integer routeDurationS, String polyline,
                       boolean hasImage, String memo, String handleCode, String createdAt) {}

    // ── 1. 캡처 판독 ─────────────────────────────

    private static final String SYSTEM = "너는 쏘카 핸들러(차량 탁송) 앱의 운행 내역 화면을 정확히 읽어 JSON 으로만 답한다.";

    private static final String PROMPT = """
            이 화면은 쏘카 핸들러의 핸들(차량 이동) 내역입니다. 오늘 날짜는 %s 입니다.
            화면에는 연도가 나오지 않습니다. 이 핸들은 %d년 기록이니 날짜의 연도는 반드시 %d 로 쓰세요.
            핸들 1건의 정보를 아래 키로 된 JSON 객체 하나로 반환하세요. 여러 건이 보이면 가장 위(가장 자세히 보이는) 1건만.

            화면 구성(“진행한 핸들” 상세): 맨 위 핸들 종류(예: 기타) 아래 영문·숫자 핸들 코드(예: J0DJFH),
            "리워드" 금액, "운행 시간" (MM/DD HH:mm ~ MM/DD HH:mm), "4.9km 운행"·"3시간 소요" 배지,
            "운행 차량" (차량번호 + 차종), "이동 경로" 의 출발지·도착지(주소 뒤에 장소명이 이어 붙어 있음),
            "핸들 내용" (예: [미션] 차량 이동).

            - handle_code: 핸들 코드
            - trip_date: "운행 시간" 시작 날짜 "YYYY-MM-DD"
            - start_time: "운행 시간" 시작 시각 "HH:mm" 24시간제 (실제 주행이 아니라 핸들 제한 시간의 시작)
            - end_time: "운행 시간" 끝 시각 "HH:mm" (제한 시간의 끝)
            - service_type: 핸들 종류 (맨 위 제목 그대로)
            - car_model: 차종
            - car_number: 차량 번호
            - fare: 리워드(원, 숫자만)
            - distance_km: "운행" 배지의 거리(km, 숫자)
            - duration_min: "소요" 배지의 시간(분, 숫자. 3시간 → 180, 2시간 50분 → 170)
            - origin_address: 출발지 주소만 (시·구·동·번지 또는 도로명·건물번호까지, 뒤의 장소명은 빼기)
            - origin_name: 출발지 장소명 1개
            - dest_address: 도착지 주소만
            - dest_name: 도착지 장소명 1개
              장소명 규칙: 주소 뒤에는 "주소상 건물명 + 실제 장소명" 이 이어 붙어 있는 경우가 많다
              (예: "…42-56 하이마트미아사거리점 롯데 하이마트 미아사거리점",
                   "…601-2 에스앤제이프리미안 기계식 주차장 S&J프리미안 오피스텔 (기계식)").
              이럴 땐 맨 뒤의 실제 장소명만 쓴다 → "롯데 하이마트 미아사거리점", "S&J프리미안 오피스텔 (기계식)".
              같은 이름을 두 번 쓰지 말 것.
            - content: "핸들 내용" 텍스트 (보이는 만큼, 줄은 " / " 로 연결. 안 보이면 null)

            규칙:
            - 오전/오후 표기는 24시간제로 바꾸세요.
            - 화면에 없는 값은 null. 추측해서 만들지 마세요.
            - JSON 외의 텍스트는 쓰지 마세요.
            """;

    /** @param year 화면에 연도가 없어 사용자가 고른 연도 */
    public Preview extract(String filename, String contentType, byte[] bytes, int year) {
        String mediaType = contentType != null ? contentType : "image/jpeg";
        if (!IMAGE_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException("이미지(jpg/png/webp)만 올릴 수 있습니다: " + mediaType);
        }
        String raw = claudeClient.completeWithImage(SYSTEM, PROMPT.formatted(LocalDate.now(), year, year), bytes, mediaType, 1024);
        if (raw == null) throw new IllegalStateException("이미지 인식 실패 (Claude 응답 없음)");
        log.info("핸들 캡처 판독 {}: {}", filename, raw);
        JsonNode n;
        try {
            n = parseObject(raw);
        } catch (Exception e) {
            throw new IllegalStateException("판독 결과를 해석하지 못했습니다");
        }
        Draft d = new Draft(
                withYear(text(n, "trip_date"), year), hhmm(text(n, "start_time")), hhmm(text(n, "end_time")),
                text(n, "service_type"), text(n, "car_model"), text(n, "car_number"),
                intOrNull(num(n, "fare")), num(n, "distance_km"), intOrNull(num(n, "duration_min")),
                text(n, "origin_name"), text(n, "origin_address"),
                text(n, "dest_name"), text(n, "dest_address"), text(n, "content"), text(n, "handle_code"));
        return resolve(d, raw);
    }

    // ── 2. 주소 → 좌표 → 경로 (수정 후 다시 찾기에도 씀) ─────────────────────────────

    public Preview resolve(Draft d, String raw) {
        List<String> warnings = new ArrayList<>();
        var origin = routeService.geocode(d.originAddress(), d.originName());
        var dest = routeService.geocode(d.destAddress(), d.destName());
        if (origin.isEmpty()) warnings.add("출발지 위치를 찾지 못했습니다 — 주소를 고쳐 다시 찾아 주세요");
        if (dest.isEmpty()) warnings.add("도착지 위치를 찾지 못했습니다 — 주소를 고쳐 다시 찾아 주세요");
        origin.filter(p -> "keyword".equals(p.source()))
                .ifPresent(p -> warnings.add("출발지는 장소 검색으로 찾음: " + nz(p.placeName()) + " (" + p.address() + ")"));
        dest.filter(p -> "keyword".equals(p.source()))
                .ifPresent(p -> warnings.add("도착지는 장소 검색으로 찾음: " + nz(p.placeName()) + " (" + p.address() + ")"));

        KakaoRouteService.Route route = null;
        if (origin.isPresent() && dest.isPresent()) {
            var a = origin.get();
            var b = dest.get();
            if (KakaoRouteService.haversineM(a.lat(), a.lon(), b.lat(), b.lon()) < 30) {
                warnings.add("출발지와 도착지가 같은 위치입니다");
            } else {
                route = routeService.route(a.lat(), a.lon(), b.lat(), b.lon());
                if ("osrm".equals(route.source())) warnings.add("카카오 길찾기 실패 → OSRM 경로로 대신함");
                if ("straight".equals(route.source())) warnings.add("길찾기 실패 → 직선으로 표시");
            }
        }
        if (d.tripDate() == null) warnings.add("날짜를 읽지 못했습니다 — 직접 입력해 주세요");
        return new Preview(d, origin.orElse(null), dest.orElse(null), route, findDuplicate(d), warnings, raw);
    }

    private Long findDuplicate(Draft d) {
        if (d.handleCode() != null && !d.handleCode().isBlank()) {
            List<Long> byCode = jdbc.queryForList("SELECT id FROM handler_trip WHERE handle_code = :c LIMIT 1",
                    Map.of("c", d.handleCode().trim()), Long.class);
            if (!byCode.isEmpty()) return byCode.get(0);
        }
        LocalDateTime start = startAt(d);
        if (start == null) return null;
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM handler_trip WHERE start_time = :start
                  AND (origin_address <=> :oa OR origin_name <=> :on) LIMIT 1
                """, new MapSqlParameterSource().addValue("start", start)
                .addValue("oa", d.originAddress()).addValue("on", d.originName()), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // ── 3. 저장 ─────────────────────────────

    public Trip save(Draft d, Long userId, String filename, String contentType, byte[] image, String raw) throws IOException {
        if (d.tripDate() == null || parseDate(d.tripDate()) == null) {
            throw new IllegalArgumentException("날짜(YYYY-MM-DD)가 필요합니다");
        }
        Preview p = resolve(d, raw);
        if (p.origin() == null || p.dest() == null) {
            throw new IllegalArgumentException(String.join(" / ", p.warnings()));
        }
        LocalDateTime start = startAt(d);
        LocalDateTime end = endAt(d, start);
        var route = p.route();

        MapSqlParameterSource ps = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tripDate", parseDate(d.tripDate()))
                .addValue("start", start)
                .addValue("end", end)
                .addValue("serviceType", cut(d.serviceType(), 50))
                .addValue("carModel", cut(d.carModel(), 50))
                .addValue("carNumber", cut(d.carNumber(), 20))
                .addValue("fare", d.fare())
                .addValue("screenKm", d.distanceKm())
                .addValue("durMin", d.durationMin())
                .addValue("oName", cut(d.originName(), 100))
                .addValue("oAddr", cut(nzOr(d.originAddress(), p.origin().address()), 200))
                .addValue("oLat", p.origin().lat())
                .addValue("oLon", p.origin().lon())
                .addValue("dName", cut(d.destName(), 100))
                .addValue("dAddr", cut(nzOr(d.destAddress(), p.dest().address()), 200))
                .addValue("dLat", p.dest().lat())
                .addValue("dLon", p.dest().lon())
                .addValue("rSrc", route != null ? route.source() : null)
                .addValue("rDist", route != null ? route.distanceM() : null)
                .addValue("rDur", route != null ? route.durationS() : null)
                .addValue("poly", route != null ? route.polyline() : null)
                .addValue("memo", cut(d.memo(), 500))
                .addValue("code", d.handleCode() == null || d.handleCode().isBlank() ? null : cut(d.handleCode().trim(), 20))
                .addValue("raw", raw);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO handler_trip
                  (user_id, trip_date, start_time, end_time, service_type, car_model, car_number, fare,
                   screen_distance_km, duration_min, origin_name, origin_address, origin_lat, origin_lon,
                   dest_name, dest_address, dest_lat, dest_lon, route_source, route_distance_m, route_duration_s,
                   polyline, memo, handle_code, raw_text)
                VALUES
                  (:userId, :tripDate, :start, :end, :serviceType, :carModel, :carNumber, :fare,
                   :screenKm, :durMin, :oName, :oAddr, :oLat, :oLon,
                   :dName, :dAddr, :dLat, :dLon, :rSrc, :rDist, :rDur,
                   :poly, :memo, :code, :raw)
                """, ps, kh, new String[]{"id"});
        long id = Objects.requireNonNull(kh.getKey()).longValue();

        if (image != null && image.length > 0) {
            String ext = switch (contentType == null ? "" : contentType) {
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "image/gif" -> "gif";
                default -> "jpg";
            };
            Path dir = imageDir();
            Files.createDirectories(dir);
            String stored = id + "." + ext;
            Files.write(dir.resolve(stored), image);
            jdbc.update("UPDATE handler_trip SET image_file = :f WHERE id = :id",
                    new MapSqlParameterSource().addValue("f", stored).addValue("id", id));
        }
        log.info("핸들 기록 저장 id={} {} {} → {} route={} {}m", id, d.tripDate(), d.originName(), d.destName(),
                route != null ? route.source() : "none", route != null ? route.distanceM() : null);
        return get(id).orElseThrow();
    }

    // ── 조회·삭제 ─────────────────────────────

    public List<Trip> list(Long userId) {
        return jdbc.query("SELECT * FROM handler_trip WHERE user_id = :u ORDER BY trip_date DESC, start_time DESC, id DESC",
                Map.of("u", userId), (rs, i) -> toTrip(rs));
    }

    public Optional<Trip> get(long id) {
        return jdbc.query("SELECT * FROM handler_trip WHERE id = :id", Map.of("id", id), (rs, i) -> toTrip(rs))
                .stream().findFirst();
    }

    public Optional<Path> imagePath(long id) {
        List<String> f = jdbc.queryForList("SELECT image_file FROM handler_trip WHERE id = :id", Map.of("id", id), String.class);
        if (f.isEmpty() || f.get(0) == null) return Optional.empty();
        Path p = imageDir().resolve(f.get(0)).normalize();
        return p.startsWith(imageDir()) && Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    public boolean delete(long id) throws IOException {
        Optional<Path> img = imagePath(id);
        int n = jdbc.update("DELETE FROM handler_trip WHERE id = :id", Map.of("id", id));
        if (n > 0 && img.isPresent()) Files.deleteIfExists(img.get());
        return n > 0;
    }

    private Trip toTrip(java.sql.ResultSet rs) throws java.sql.SQLException {
        var start = rs.getTimestamp("start_time");
        var end = rs.getTimestamp("end_time");
        var created = rs.getTimestamp("created_at");
        return new Trip(rs.getLong("id"), rs.getDate("trip_date").toLocalDate().toString(),
                start != null ? start.toLocalDateTime().toLocalTime().toString() : null,
                end != null ? end.toLocalDateTime().toLocalTime().toString() : null,
                rs.getString("service_type"), rs.getString("car_model"), rs.getString("car_number"),
                (Integer) rs.getObject("fare", Integer.class), (Double) rs.getObject("screen_distance_km", Double.class),
                (Integer) rs.getObject("duration_min", Integer.class),
                rs.getString("origin_name"), rs.getString("origin_address"),
                (Double) rs.getObject("origin_lat", Double.class), (Double) rs.getObject("origin_lon", Double.class),
                rs.getString("dest_name"), rs.getString("dest_address"),
                (Double) rs.getObject("dest_lat", Double.class), (Double) rs.getObject("dest_lon", Double.class),
                rs.getString("route_source"), (Integer) rs.getObject("route_distance_m", Integer.class),
                (Integer) rs.getObject("route_duration_s", Integer.class), rs.getString("polyline"),
                rs.getString("image_file") != null, rs.getString("memo"), rs.getString("handle_code"),
                created != null ? created.toLocalDateTime().toString() : null);
    }

    // ── 유틸 ─────────────────────────────

    private Path imageDir() {
        return Paths.get(basePath).toAbsolutePath().normalize().resolve("trips");
    }

    static LocalDateTime startAt(Draft d) {
        LocalDate date = parseDate(d.tripDate());
        LocalTime t = parseTime(d.startTime());
        return date != null && t != null ? date.atTime(t) : null;
    }

    /** 종료 시각이 시작보다 이르면 자정을 넘긴 것으로 본다. */
    static LocalDateTime endAt(Draft d, LocalDateTime start) {
        LocalDate date = parseDate(d.tripDate());
        LocalTime t = parseTime(d.endTime());
        if (date == null || t == null) return null;
        LocalDateTime end = date.atTime(t);
        if (start != null && end.isBefore(start)) end = end.plusDays(1);
        return end;
    }

    /** 판독한 날짜의 연도를 사용자가 고른 연도로 강제한다 (화면에 연도가 없으므로). */
    static String withYear(String date, int year) {
        if (date == null) return null;
        var m = java.util.regex.Pattern.compile("(\\d{1,2})\\D+(\\d{1,2})\\D*$").matcher(date.trim());
        if (!m.find()) return null;
        try {
            return LocalDate.of(year, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))).toString();
        } catch (Exception e) {
            return null;
        }
    }

    static LocalDate parseDate(String s) {
        if (s == null) return null;
        try {
            return LocalDate.parse(s.trim().replace('.', '-').replace('/', '-'));
        } catch (Exception e) {
            return null;
        }
    }

    static LocalTime parseTime(String s) {
        if (s == null) return null;
        try {
            return LocalTime.parse(hhmm(s), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return null;
        }
    }

    /** "9:05", "09:05:00" → "09:05" */
    static String hhmm(String s) {
        if (s == null) return null;
        var m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(s);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1));
        if (h > 23) return null;
        return String.format("%02d:%s", h, m.group(2));
    }

    private JsonNode parseObject(String raw) throws Exception {
        String s = raw.trim();
        int o = s.indexOf('{'), a = s.indexOf('[');
        if (a >= 0 && (o < 0 || a < o)) {
            JsonNode arr = objectMapper.readTree(s.substring(a, s.lastIndexOf(']') + 1));
            return arr.path(0);
        }
        return objectMapper.readTree(s.substring(o, s.lastIndexOf('}') + 1));
    }

    static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim();
        // 모델이 null 을 문자열 "null" 로 주는 경우가 있다
        return s.isBlank() || s.equalsIgnoreCase("null") || s.equals("-") ? null : s;
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

    private static Integer intOrNull(Double d) {
        return d == null ? null : (int) Math.round(d);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String nzOr(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}

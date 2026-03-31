package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.WindyWeatherDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    @Value("${windy.api-key}")
    private String windyApiKey;

    @Value("${windy.api-url}")
    private String windyApiUrl;

    @Value("${windy.model:gfs}")
    private String model;

    private final RestTemplate restTemplate;

    public WindyWeatherDto getWeatherForPoint(double lat, double lon, String date) {
        Map<String, Object> body = Map.of(
            "lat",        lat,
            "lon",        lon,
            "model",      model,
            "parameters", List.of("temp", "wind", "precip"),
            "levels",     List.of("surface"),
            "key",        windyApiKey
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> res = restTemplate.postForEntity(windyApiUrl, request, Map.class);
            return parseWindyResponse(res.getBody(), date);
        } catch (Exception e) {
            log.error("Windy API 호출 실패 lat={} lon={} date={}: {}", lat, lon, date, e.getMessage());
            return WindyWeatherDto.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private WindyWeatherDto parseWindyResponse(Map<String, Object> data, String targetDate) {
        if (data == null) return WindyWeatherDto.empty();

        List<Number> timestamps = (List<Number>) data.get("ts");
        List<Number> temps      = (List<Number>) data.get("temp-surface");
        List<Number> windU      = (List<Number>) data.get("wind_u-surface");
        List<Number> windV      = (List<Number>) data.get("wind_v-surface");
        List<Number> precips    = (List<Number>) data.get("precip-surface");

        if (timestamps == null || temps == null || windU == null || windV == null) {
            log.warn("Windy 응답 데이터 누락: {}", data.keySet());
            return WindyWeatherDto.empty();
        }

        // 09:00 KST 기준 인덱스
        long targetMs = LocalDate.parse(targetDate)
            .atTime(9, 0)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant()
            .toEpochMilli();

        int idx = findClosestIndex(timestamps, targetMs);

        double u = windU.get(idx).doubleValue();
        double v = windV.get(idx).doubleValue();
        double windSpeed = Math.sqrt(u * u + v * v) * 3.6; // m/s → km/h
        double windDir   = (Math.toDegrees(Math.atan2(-u, -v)) + 360) % 360;
        double tempC     = temps.get(idx).doubleValue() - 273.15; // K → °C
        double precip    = (precips != null && idx < precips.size())
            ? precips.get(idx).doubleValue() : 0.0;

        // 시간대별 예보 (06, 09, 12, 15, 18, 21시)
        List<WindyWeatherDto.HourlyWeather> hourly = List.of(6, 9, 12, 15, 18, 21).stream()
            .map(hour -> {
                long hourMs = LocalDate.parse(targetDate)
                    .atTime(hour, 0)
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .toInstant()
                    .toEpochMilli();
                int hIdx = findClosestIndex(timestamps, hourMs);

                double hu = windU.get(hIdx).doubleValue();
                double hv = windV.get(hIdx).doubleValue();
                double hWind = Math.sqrt(hu * hu + hv * hv) * 3.6;
                double hDir  = (Math.toDegrees(Math.atan2(-hu, -hv)) + 360) % 360;
                double hTemp = temps.get(hIdx).doubleValue() - 273.15;
                double hPrecip = (precips != null && hIdx < precips.size())
                    ? precips.get(hIdx).doubleValue() : 0.0;

                return WindyWeatherDto.HourlyWeather.builder()
                    .time(String.format("%02d:00", hour))
                    .temp(Math.round(hTemp * 10.0) / 10.0)
                    .windSpeed(Math.round(hWind * 10.0) / 10.0)
                    .windDir((int) hDir)
                    .precip(Math.round(hPrecip * 10.0) / 10.0)
                    .build();
            })
            .toList();

        return WindyWeatherDto.builder()
            .temp(Math.round(tempC * 10.0) / 10.0)
            .windSpeed(Math.round(windSpeed * 10.0) / 10.0)
            .windDir((int) windDir)
            .precip(Math.round(precip * 10.0) / 10.0)
            .hourly(hourly)
            .build();
    }

    private int findClosestIndex(List<Number> timestamps, long targetMs) {
        int idx = 0;
        long minDiff = Long.MAX_VALUE;
        for (int i = 0; i < timestamps.size(); i++) {
            long diff = Math.abs(timestamps.get(i).longValue() - targetMs);
            if (diff < minDiff) {
                minDiff = diff;
                idx = i;
            }
        }
        return idx;
    }
}

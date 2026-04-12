package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.WindyWeatherDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final RestTemplate restTemplate;

    private static final String OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude={lat}&longitude={lon}" +
                    "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,precipitation" +
                    "&timezone=Asia%2FSeoul" +
                    "&forecast_days=16";

    public WindyWeatherDto getWeatherForPoint(double lat, double lon, String date) {
        try {
            Map response = restTemplate.getForObject(
                    OPEN_METEO_URL, Map.class,
                    Map.of("lat", lat, "lon", lon)
            );
            return parseOpenMeteoResponse(response, date);
        } catch (Exception e) {
            log.error("Open-Meteo API 호출 실패 lat={} lon={} date={}: {}", lat, lon, date, e.getMessage());
            return WindyWeatherDto.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private WindyWeatherDto parseOpenMeteoResponse(Map<String, Object> data, String targetDate) {
        if (data == null) return WindyWeatherDto.empty();

        Map<String, Object> hourlyData = (Map<String, Object>) data.get("hourly");
        if (hourlyData == null) return WindyWeatherDto.empty();

        List<String> times      = (List<String>) hourlyData.get("time");
        List<Number> temps      = (List<Number>) hourlyData.get("temperature_2m");
        List<Number> windSpeeds = (List<Number>) hourlyData.get("wind_speed_10m");
        List<Number> windDirs   = (List<Number>) hourlyData.get("wind_direction_10m");
        List<Number> precips    = (List<Number>) hourlyData.get("precipitation");

        if (times == null || temps == null || windSpeeds == null || windDirs == null) {
            log.warn("Open-Meteo 응답 데이터 누락");
            return WindyWeatherDto.empty();
        }

        // 09:00 KST 기준값
        String target09 = targetDate + "T09:00";
        int idx09 = times.indexOf(target09);
        if (idx09 < 0) {
            log.warn("날짜 {} 에 해당하는 시간 데이터 없음", targetDate);
            return WindyWeatherDto.empty();
        }

        // 시간대별 예보 (06, 09, 12, 15, 18, 21시)
        List<WindyWeatherDto.HourlyWeather> hourly = List.of(6, 9, 12, 15, 18, 21).stream()
                .map(hour -> {
                    String timeKey = String.format("%sT%02d:00", targetDate, hour);
                    int hIdx = times.indexOf(timeKey);
                    if (hIdx < 0) return null;

                    return WindyWeatherDto.HourlyWeather.builder()
                            .time(String.format("%02d:00", hour))
                            .temp(round(temps.get(hIdx).doubleValue()))
                            .windSpeed(round(windSpeeds.get(hIdx).doubleValue()))
                            .windDir(windDirs.get(hIdx).intValue())
                            .precip(precips != null ? round(precips.get(hIdx).doubleValue()) : 0.0)
                            .build();
                })
                .filter(h -> h != null)
                .toList();

        return WindyWeatherDto.builder()
                .temp(round(temps.get(idx09).doubleValue()))
                .windSpeed(round(windSpeeds.get(idx09).doubleValue()))
                .windDir(windDirs.get(idx09).intValue())
                .precip(precips != null ? round(precips.get(idx09).doubleValue()) : 0.0)
                .hourly(hourly)
                .build();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

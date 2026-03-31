package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class WindyWeatherDto {

    private double temp;        // 기온 (°C)
    private double windSpeed;   // 풍속 (km/h)
    private int    windDir;     // 풍향 (0°=북, 시계방향)
    private double precip;      // 강수량 (mm)

    @Builder.Default
    private List<HourlyWeather> hourly = Collections.emptyList();

    public static WindyWeatherDto empty() {
        return WindyWeatherDto.builder()
            .temp(0.0).windSpeed(0.0).windDir(0).precip(0.0)
            .hourly(Collections.emptyList())
            .build();
    }

    @Getter
    @Builder
    public static class HourlyWeather {
        private String time;       // "09:00"
        private double temp;
        private double windSpeed;
        private int    windDir;
        private double precip;
    }
}

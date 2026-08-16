package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherDto {

    private Double temperature;  // 기온 (°C)
    private Double humidity;     // 습도 (%)
    private Double windSpeed;    // 풍속
    private Double pressure;     // 기압 (hPa)
    private Integer windDeg;     // 풍향 (도, 0~360, 북풍=0)
}

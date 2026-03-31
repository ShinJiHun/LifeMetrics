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
}

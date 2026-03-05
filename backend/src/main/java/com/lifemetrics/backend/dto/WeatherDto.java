// dto/WeatherDto.java
package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherDto {
    private Double temperature;
    private Double humidity;
    private Double windSpeed;
    private Double pressure;
}
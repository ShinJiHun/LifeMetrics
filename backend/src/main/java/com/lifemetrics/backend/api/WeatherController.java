package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.WindyWeatherDto;
import com.lifemetrics.backend.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    /**
     * GET /api/weather/point?lat=36.5&lon=127.1&date=2026-04-04
     */
    @GetMapping("/point")
    public WindyWeatherDto getWeatherForPoint(
        @RequestParam double lat,
        @RequestParam double lon,
        @RequestParam String date
    ) {
        return weatherService.getWeatherForPoint(lat, lon, date);
    }
}

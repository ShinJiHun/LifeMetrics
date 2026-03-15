// service/ActivityService.java
package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.dto.*;
import com.lifemetrics.backend.entity.*;
import com.lifemetrics.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityCoreRepository coreRepository;
    private final ActivityPointRepository pointRepository;
    private final ActivityWeatherRepository weatherRepository;
    private final AiAnalysisRepository analysisRepo;
    private final ActivityCoreRepository activityRepo;  // ← 이거 있어야 해요!
    private final ObjectMapper objectMapper;

    public List<ActivitySummaryDto> getActivityList(Long userId, LocalDate startDate, LocalDate endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        List<ActivityCore> activities;

        if (startDate != null && endDate != null) {
            activities = coreRepository.findByUserIdAndDateRange(
                    userId,
                    startDate.atStartOfDay(),
                    endDate.plusDays(1).atStartOfDay(),
                    pageable
            );
        } else {
            activities = coreRepository.findByUserId(userId, pageable);
        }

        return activities.stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    public ActivityDetailDto getActivityDetail(Long id) {
        ActivityCore core = coreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found: " + id));

        List<ActivityPoint> points = pointRepository.findByActivityCoreIdOrderBySeqAsc(id);
        ActivityWeather weather = weatherRepository.findByActivityCoreId(id).orElse(null);

        return ActivityDetailDto.builder()
                .id(core.getId())
                .filename(core.getFilename())
                .startTime(core.getStartTime())
                .endTime(core.getEndTime())
                .totalDistance(core.getTotalDistance())
                .movingTime(core.getMovingTime())
                .elapsedTime(core.getElapsedTime())
                .avgSpeed(core.getAvgSpeed())
                .maxSpeed(core.getMaxSpeed())
                .totalAscent(core.getTotalAscent())
                .totalDescent(core.getTotalDescent())
                .avgHeartRate(core.getAvgHeartRate())
                .maxHeartRate(core.getMaxHeartRate())
                .avgPower(core.getAvgPower())
                .maxPower(core.getMaxPower())
                .avgCadence(core.getAvgCadence())
                .uphillDistance(core.getUphillDistance())
                .flatDistance(core.getFlatDistance())
                .downDistance(core.getDownDistance())
                .startLat(core.getStartLat())
                .startLon(core.getStartLon())
                .endLat(core.getEndLat())
                .endLon(core.getEndLon())
                .polyline(core.getPolyline())
                .gearName(core.getGearName())
                .weather(weather != null ? toWeatherDto(weather) : null)
                .points(points.stream().map(this::toPointDto).collect(Collectors.toList()))
                .build();
    }

    public MonthlyStatsDto getMonthlyStats(Long userId, int year, int month) {
        LocalDateTime startDate = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime endDate = startDate.plusMonths(1);

        Object[] result = coreRepository.getMonthlyStats(userId, startDate, endDate);

        Long count = (Long) result[0];
        Double totalDistance = (Double) result[1];
        Long totalMovingTime = result[2] != null ? ((Number) result[2]).longValue() : 0L;
        Double totalAscent = (Double) result[3];
        Double avgSpeed = (Double) result[4];

        return MonthlyStatsDto.builder()
                .year(year)
                .month(month)
                .rideCount(count != null ? count.intValue() : 0)
                .totalDistance(totalDistance)
                .totalMovingTime(totalMovingTime.intValue())
                .totalAscent(totalAscent)
                .avgSpeed(avgSpeed)
                .build();
    }

    private ActivitySummaryDto toSummaryDto(ActivityCore core) {
        return ActivitySummaryDto.builder()
                .id(core.getId())
                .filename(core.getFilename())
                .startTime(core.getStartTime())
                .endTime(core.getEndTime())
                .totalDistance(core.getTotalDistance())
                .movingTime(core.getMovingTime())
                .elapsedTime(core.getElapsedTime())
                .avgSpeed(core.getAvgSpeed())
                .maxSpeed(core.getMaxSpeed())
                .totalAscent(core.getTotalAscent())
                .totalDescent(core.getTotalDescent())
                .gearName(core.getGearName())
                .startLat(core.getStartLat())
                .startLon(core.getStartLon())
                .endLat(core.getEndLat())
                .endLon(core.getEndLon())
                .polyline(core.getPolyline())
                .avgHeartRate(core.getAvgHeartRate())
                .maxHeartRate(core.getMaxHeartRate())
                .avgPower(core.getAvgPower())
                .avgCadence(core.getAvgCadence())
                .calories(core.getCalories())
                .build();
    }

    private ActivityPointDto toPointDto(ActivityPoint point) {
        return ActivityPointDto.builder()
                .seq(point.getSeq())
                .pointTime(point.getPointTime())
                .lat(point.getLat())
                .lon(point.getLon())
                .altitude(point.getAltitude())
                .distance(point.getDistance())
                .speed(point.getSpeed())
                .cadence(point.getCadence())
                .heartRate(point.getHeartRate())
                .power(point.getPower())
                .slope(point.getSlope())
                .heading(point.getHeading())
                .build();
    }

    private WeatherDto toWeatherDto(ActivityWeather weather) {
        return WeatherDto.builder()
                .temperature(weather.getTemperature())
                .humidity(weather.getHumidity())
                .windSpeed(weather.getWindSpeed())
                .pressure(weather.getPressure())
                .build();
    }

}

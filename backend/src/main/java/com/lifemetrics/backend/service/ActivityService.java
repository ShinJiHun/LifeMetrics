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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final SegmentEffortRepository segmentEffortRepository;
    private final SegmentRepository segmentRepository;

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


    public List<ActivitySegmentDto> getActivitySegments(Long activityCoreId) {
        List<SegmentEffort> efforts = segmentEffortRepository.findByActivityCoreId(activityCoreId);
        if (efforts.isEmpty()) return List.of();

        // segment ID 목록 추출 → 한 번에 IN 절로 조회 (쿼리 1번)
        List<Long> segmentIds = efforts.stream()
                .map(SegmentEffort::getSegmentId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Segment> segmentMap = segmentRepository.findAllById(segmentIds)
                .stream()
                .collect(Collectors.toMap(Segment::getId, s -> s));

        return efforts.stream().map(effort -> {
            Segment seg = segmentMap.get(effort.getSegmentId()); // Map 조회, DB 안 탐
            return ActivitySegmentDto.builder()
                    .effortId(effort.getId())
                    .segmentId(effort.getSegmentId())
                    .segmentName(seg != null ? seg.getName() : "알 수 없음")
                    .distance(seg != null ? seg.getDistance() : null)
                    .elevationGain(seg != null ? seg.getElevationGain() : null)
                    .avgGrade(seg != null ? seg.getAvgGrade() : null)
                    .polyline(seg != null ? seg.getPolyline() : null)
                    .startTime(effort.getStartTime())
                    .elapsedTimeSec(effort.getElapsedTimeSec())
                    .movingTimeSec(effort.getMovingTimeSec())
                    .avgSpeed(effort.getAvgSpeed())
                    .maxSpeed(effort.getMaxSpeed())
                    .avgHeartRate(effort.getAvgHeartRate())
                    .maxHeartRate(effort.getMaxHeartRate())
                    .avgPower(effort.getAvgPower())
                    .maxPower(effort.getMaxPower())  // ★ 추가
                    .avgCadence(effort.getAvgCadence() != null ? effort.getAvgCadence().doubleValue() : null)
                    .startDistanceM(effort.getStartDistanceM())
                    .endDistanceM(effort.getEndDistanceM())
                    .prRank(effort.getPrRank())
                    .build();
        }).sorted(Comparator.comparing(
                ActivitySegmentDto::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        )).collect(Collectors.toList());
    }

    public ActivitySummaryDto getActivitySummary(Long id) {
        ActivityCore core = coreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found: " + id));
        return toSummaryDto(core);
    }

}

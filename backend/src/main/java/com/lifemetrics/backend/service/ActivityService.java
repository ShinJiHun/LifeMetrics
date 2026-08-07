// service/ActivityService.java
package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.dto.*;
import com.lifemetrics.backend.entity.*;
import com.lifemetrics.backend.mapper.ActivityDetailMapper;
import com.lifemetrics.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
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
    private final ObjectMapper objectMapper;
    private final SegmentEffortRepository segmentEffortRepository;
    private final SegmentRepository segmentRepository;
    private final ActivityDetailMapper detailMapper;
    private final GearResolveService gearResolveService;

    private static final int DEFAULT_RESTING_HR = 60;
    private static final int DEFAULT_MAX_HR = 190;

    // ═══════════════════════════════════════════════════════════════
    // 라이딩 목록
    // ═══════════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════════
    // 라이딩 상세 (포인트 포함)
    // ═══════════════════════════════════════════════════════════════
    public ActivityDetailDto getActivityDetail(Long id) {
        ActivityCore core = coreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found: " + id));

        List<ActivityPoint> points = pointRepository.findByActivityCoreIdOrderBySeqAsc(id);
        ActivityWeather weather = weatherRepository.findByActivityCoreId(id).orElse(null);
        GearContext gearContext = gearResolveService.resolve(core.getUserId(), core.getStartTime(), core.getBikeId());

        return ActivityDetailDto.builder()
                .id(core.getId())
                .filename(core.getFilename())
                .name(core.getName())
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
                .maxCadence(core.getMaxCadence())
                .calories(core.getCalories())
                .uphillDistance(core.getUphillDistance())
                .flatDistance(core.getFlatDistance())
                .downDistance(core.getDownDistance())
                .startLat(core.getStartLat())
                .startLon(core.getStartLon())
                .endLat(core.getEndLat())
                .endLon(core.getEndLon())
                .polyline(core.getPolyline())
                .gearContext(gearContext)
                .locationName(core.getLocationName())
                .relativeEffort(core.getRelativeEffort())
                .weather(weather != null ? toWeatherDto(weather) : null)
                .points(points.stream().map(this::toPointDto).collect(Collectors.toList()))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // 월간 통계
    // ═══════════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════════
    // 라이딩 세그먼트
    // ═══════════════════════════════════════════════════════════════
    public List<ActivitySegmentDto> getActivitySegments(Long activityCoreId) {
        List<SegmentEffort> efforts = segmentEffortRepository.findByActivityCoreId(activityCoreId);
        if (efforts.isEmpty()) return List.of();

        List<Long> segmentIds = efforts.stream()
                .map(SegmentEffort::getSegmentId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Segment> segmentMap = segmentRepository.findAllById(segmentIds)
                .stream()
                .collect(Collectors.toMap(Segment::getId, s -> s));

        return efforts.stream().map(effort -> {
            Segment seg = segmentMap.get(effort.getSegmentId());
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
                    .maxPower(effort.getMaxPower())
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

    // ═══════════════════════════════════════════════════════════════
    // 라이딩 요약 (캐시 + 보강)
    // ═══════════════════════════════════════════════════════════════
    @Transactional
    public ActivitySummaryDto getActivitySummary(Long id) {
        ActivityCore core = coreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found: " + id));
        return buildSummaryWithEnrichment(core);
    }

    /**
     * 라이드 제목 편집 — 가볍게 처리.
     * <p>
     * 보강 로직(RE/위치 재계산)을 건너뛰고 name 컬럼만 update.
     * 이렇게 하지 않으면 동시 요청(getActivitySummary와 PATCH가 같은 row의 update 트리거)으로
     * MariaDB optimistic lock 충돌 ('Record has changed') 발생.
     */
    @Transactional
    public ActivitySummaryDto updateActivityName(Long id, String name) {
        ActivityCore core = coreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found: " + id));

        // 제목만 업데이트 (자동 생성 이름은 null 저장 시 다음 summary 호출 때 채워짐)
        String trimmed = (name != null && !name.isBlank()) ? name.trim() : null;
        core.setName(trimmed);

        // dirty checking으로 자동 저장 (RE/locationName 등 다른 필드는 건드리지 않음)
        coreRepository.save(core);

        // 응답 — 보강 없이 현재 캐시된 값 그대로 반환
        return toSummaryDto(core);
    }

    // ═══════════════════════════════════════════════════════════════
    // private helpers
    // ═══════════════════════════════════════════════════════════════

    private ActivitySummaryDto buildSummaryWithEnrichment(ActivityCore core) {
        // RE 캐시 miss인 경우에만 포인트 로드
        List<ActivityPoint> points = core.getRelativeEffort() == null
                ? pointRepository.findByActivityCoreIdAndHeartRateNotNullOrderBySeqAsc(core.getId())
                : Collections.emptyList();

        int restingHr = DEFAULT_RESTING_HR;
        int maxHr = DEFAULT_MAX_HR;

        ActivityDetailMapper.EnrichedFields enriched =
                detailMapper.enrich(core, points, restingHr, maxHr);

        if (enriched.relativeEffortNeedsSave) {
            core.setRelativeEffort(enriched.relativeEffort);
        }
        if (enriched.locationNameNeedsSave) {
            core.setLocationName(enriched.locationName);
        }
        if (enriched.nameNeedsSave) {
            core.setName(enriched.name);
        }

        return toSummaryDto(core);
    }

    // ═══════════════════════════════════════════════════════════════
    // Entity → DTO 변환
    // ═══════════════════════════════════════════════════════════════

    private ActivitySummaryDto toSummaryDto(ActivityCore core) {
        WeatherDto weather = weatherRepository.findByActivityCoreId(core.getId())
                .map(this::toWeatherDto)
                .orElse(null);
        GearContext gearContext = gearResolveService.resolve(core.getUserId(), core.getStartTime(), core.getBikeId());

        return ActivitySummaryDto.builder()
                .id(core.getId())
                .filename(core.getFilename())
                .name(core.getName())
                .startTime(core.getStartTime())
                .endTime(core.getEndTime())
                .totalDistance(core.getTotalDistance())
                .movingTime(core.getMovingTime())
                .elapsedTime(core.getElapsedTime())
                .avgSpeed(core.getAvgSpeed())
                .maxSpeed(core.getMaxSpeed())
                .totalAscent(core.getTotalAscent())
                .totalDescent(core.getTotalDescent())
                .startLat(core.getStartLat())
                .startLon(core.getStartLon())
                .endLat(core.getEndLat())
                .endLon(core.getEndLon())
                .polyline(core.getPolyline())
                .avgHeartRate(core.getAvgHeartRate())
                .maxHeartRate(core.getMaxHeartRate())
                .avgPower(core.getAvgPower())
                .maxPower(core.getMaxPower())
                .hasPower(core.getHasPower())
                .avgCadence(core.getAvgCadence())
                .maxCadence(core.getMaxCadence())
                .calories(core.getCalories())
                .locationName(core.getLocationName())
                .relativeEffort(core.getRelativeEffort())
                .weather(weather)
                .gearContext(gearContext)
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

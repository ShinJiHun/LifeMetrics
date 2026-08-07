// service/ActivityDetailMapper.java
package com.lifemetrics.backend.mapper;

import com.lifemetrics.backend.dto.ActivitySummaryDto;
import com.lifemetrics.backend.dto.WeatherDto;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.ActivityPoint;
import com.lifemetrics.backend.service.RelativeEffortCalculator;
import com.lifemetrics.backend.service.ReverseGeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.util.List;

/**
 * ActivityCore 엔티티 -> ActivitySummaryDto 변환 + 부가 필드 보강 담당.
 * <p>
 * 보강하는 필드:
 * <ul>
 *   <li>{@code name} - 라이드 제목 (없으면 자동 생성)</li>
 *   <li>{@code locationName} - 역지오코딩 결과 (없으면 Mapbox 호출)</li>
 *   <li>{@code relativeEffort} - TRIMP 기반 RE (없으면 즉석 계산)</li>
 * </ul>
 * <p>
 * 위 세 값은 한 번 계산하면 비싸므로 ActivityCore에 저장(캐시)해두는 것을 권장.
 * 호출자(Service)에서 mapper 호출 후 변경된 값들을 save 하세요.
 */
@Component
@RequiredArgsConstructor
public class ActivityDetailMapper {

    private final RelativeEffortCalculator reCalculator;
    private final ReverseGeocodingService geocodingService;

    /**
     * /api/activity/{id}/summary 응답 빌드.
     *
     * @param activity   ActivityCore 엔티티
     * @param points     RE 계산용 포인트 (이미 캐시된 RE가 있으면 빈 리스트도 OK)
     * @param weatherDto 날씨 (없어도 무방)
     * @param restingHr  사용자 안정시 심박 (User 엔티티에서 조회)
     * @param maxHr      사용자 최대심박 (User 엔티티에서 조회)
     */
    public ActivitySummaryDto toSummary(ActivityCore activity,
                                        List<ActivityPoint> points,
                                        WeatherDto weatherDto,
                                        int restingHr,
                                        int maxHr) {

        EnrichedFields enriched = enrich(activity, points, restingHr, maxHr);

        return ActivitySummaryDto.builder()
                .id(activity.getId())
                .filename(activity.getFilename())
                .name(enriched.name)
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .totalDistance(activity.getTotalDistance())
                .movingTime(activity.getMovingTime())
                .elapsedTime(activity.getElapsedTime())
                .avgSpeed(activity.getAvgSpeed())
                .maxSpeed(activity.getMaxSpeed())
                .totalAscent(activity.getTotalAscent())
                .totalDescent(activity.getTotalDescent())
                .startLat(activity.getStartLat())
                .startLon(activity.getStartLon())
                .endLat(activity.getEndLat())
                .endLon(activity.getEndLon())
                .polyline(activity.getPolyline())
                .avgHeartRate(activity.getAvgHeartRate())
                .maxHeartRate(activity.getMaxHeartRate())
                .avgPower(activity.getAvgPower())
                .maxPower(activity.getMaxPower())
                .hasPower(activity.getHasPower())
                .avgCadence(activity.getAvgCadence())
                .maxCadence(activity.getMaxCadence())
                .calories(activity.getCalories())
                .locationName(enriched.locationName)
                .relativeEffort(enriched.relativeEffort)
                .weather(weatherDto)
                .build();
    }

    /**
     * 보강 필드 계산. 호출자가 enriched 결과를 받아서 ActivityCore에 캐시할 수 있도록
     * EnrichedFields 객체를 반환하는 public 변형도 노출.
     */
    public EnrichedFields enrich(ActivityCore activity,
                                 List<ActivityPoint> points,
                                 int restingHr,
                                 int maxHr) {
        EnrichedFields f = new EnrichedFields();

        // 1) Relative Effort
        f.relativeEffort = activity.getRelativeEffort();
        if (f.relativeEffort == null) {
            f.relativeEffort = reCalculator.calculate(points, restingHr, maxHr);
            if (f.relativeEffort == null
                    && activity.getMovingTime() != null
                    && activity.getAvgSpeed() != null) {
                f.relativeEffort = reCalculator.estimateFromSpeed(
                        activity.getMovingTime(),
                        activity.getAvgSpeed()
                );
            }
            f.relativeEffortNeedsSave = f.relativeEffort != null;
        }

        // 2) 출발지명 (없으면 1회 호출)
        f.locationName = activity.getLocationName();
        if (f.locationName == null
                && activity.getStartLat() != null
                && activity.getStartLon() != null) {
            f.locationName = geocodingService.reverseGeocode(
                    activity.getStartLat(),
                    activity.getStartLon()
            );
            f.locationNameNeedsSave = f.locationName != null;
        }

        // 3) 라이드 제목 (사용자가 입력 안 했으면 자동 생성)
        // 자동 생성된 이름은 매번 새로 만들어도 가볍지만, 한번 저장해두면 사용자가 편집해도
        // 안정적으로 유지됨
        f.name = activity.getName();
        if (f.name == null || f.name.isBlank()) {
            f.name = generateDefaultName(activity, f.locationName);
            f.nameNeedsSave = f.name != null;
        }

        return f;
    }

    private String generateDefaultName(ActivityCore activity, String locationName) {
        StringBuilder sb = new StringBuilder();

        // 출발지 (있으면 앞에)
        if (locationName != null) {
            sb.append(locationName).append(" ");
        }

        // 시간대
        if (activity.getStartTime() != null) {
            int hour = activity.getStartTime()
                    .atZone(ZoneOffset.UTC)                    // 저장값은 UTC
                    .withZoneSameInstant(ZoneId.of("Asia/Seoul"))  // KST로 변환
                    .getHour();                                 // 이제 07시
            if (hour < 6) sb.append("새벽");
            else if (hour < 12) sb.append("아침");
            else if (hour < 18) sb.append("오후");
            else sb.append("저녁");
            sb.append(" 라이딩");
        } else {
            sb.append("라이딩");
        }

        // 거리 (장거리만 표기 — 100km 이상)
        if (activity.getTotalDistance() != null) {
            double km = activity.getTotalDistance() / 1000.0;
            if (km >= 100) {
                sb.append(" (").append(Math.round(km)).append("km)");
            }
        }

        return sb.toString();
    }

    /**
     * 호출자(Service)가 결과를 받아서 ActivityCore에 캐시 저장할 수 있도록
     * 필드별 "저장 필요 여부" 플래그도 함께 제공합니다.
     */
    public static class EnrichedFields {
        public String name;
        public boolean nameNeedsSave;

        public String locationName;
        public boolean locationNameNeedsSave;

        public Integer relativeEffort;
        public boolean relativeEffortNeedsSave;

        /**
         * 셋 중 하나라도 새로 계산된 값이 있는지.
         * Service에서 save 호출 여부를 판단할 때 사용.
         */
        public boolean anyNeedsSave() {
            return nameNeedsSave || locationNameNeedsSave || relativeEffortNeedsSave;
        }
    }
}

package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 라이딩 상세 분석 (존 분포, 파워커브, 인터벌, 그래프용 시계열).
 * activity_point 로부터 계산한다.
 */
@Data
@Builder
public class ActivityAnalyticsDto {

    private RiderZoneSettingDto zoneSetting;

    // ── 파워 요약 (파워 데이터가 없으면 null) ──
    private Integer normalizedPower;
    private Integer avgPower;
    private Double intensityFactor;
    private Integer trainingStress;
    private Double variabilityIndex;
    private Integer workKj;
    private Double wattsPerKg;

    private List<ZoneTime> powerZones;
    private List<ZoneTime> hrZones;
    private List<CurvePoint> powerCurve;
    private List<Interval> intervals;
    private List<StreamPoint> streams;

    @Data
    @Builder
    public static class ZoneTime {
        private String zone;      // Z1..Z7
        private String name;      // 회복, 지구력 ...
        private Integer min;      // 하한 (W 또는 bpm)
        private Integer max;      // 상한 (마지막 존은 null)
        private int seconds;
        private double percent;
    }

    @Data
    @Builder
    public static class CurvePoint {
        private int seconds;
        private String label;     // 5초, 1분, 20분 ...
        private int watts;
    }

    @Data
    @Builder
    public static class Interval {
        private double startKm;
        private int durationSec;
        private int avgPower;
        private int maxPower;
        private Integer avgHr;
        private Integer avgCadence;
        private String zone;
    }

    // 거리 구간 평균값 (그래프용, 약 800개로 다운샘플)
    @Data
    @Builder
    public static class StreamPoint {
        private double km;
        private Double speed;
        private Integer power;
        private Integer cadence;
        private Integer hr;
        private Double altitude;
    }
}

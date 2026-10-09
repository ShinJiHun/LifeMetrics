package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.ActivityAnalyticsDto;
import com.lifemetrics.backend.dto.ActivityAnalyticsDto.CurvePoint;
import com.lifemetrics.backend.dto.ActivityAnalyticsDto.Interval;
import com.lifemetrics.backend.dto.ActivityAnalyticsDto.StreamPoint;
import com.lifemetrics.backend.dto.ActivityAnalyticsDto.ZoneTime;
import com.lifemetrics.backend.dto.RiderZoneSettingDto;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.ActivityPoint;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.ActivityPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 라이딩 상세 분석: 파워존/심박존, 파워커브, 인터벌, 그래프용 시계열.
 * <p>
 * 포인트는 대부분 1초 간격이지만 스마트 레코딩/정지 구간이 섞여 있어서,
 * 먼저 "이동 중인 1초 단위 시계열"로 펼친 뒤 계산한다.
 */
@Service
@RequiredArgsConstructor
public class ActivityAnalyticsService {

    /** 이보다 긴 포인트 간격은 일시정지로 보고 1초만 센다 */
    private static final int MAX_FILL_GAP_SEC = 5;
    private static final int ROLLING_SEC = 30;
    private static final int STREAM_BUCKETS = 800;

    private static final int[] POWER_ZONE_PCT = {0, 55, 75, 90, 105, 120, 150};
    private static final String[] POWER_ZONE_NAMES = {"회복", "지구력", "템포", "역치", "VO2max", "무산소", "신경근"};
    private static final int[] HR_ZONE_PCT = {0, 60, 70, 80, 90};
    private static final String[] HR_ZONE_NAMES = {"회복", "지구력", "템포", "역치", "최대"};

    private static final int[] CURVE_SECONDS = {5, 15, 30, 60, 120, 300, 600, 1200, 1800, 3600, 7200, 10800, 18000};
    private static final String[] CURVE_LABELS = {"5초", "15초", "30초", "1분", "2분", "5분", "10분", "20분", "30분", "1시간", "2시간", "3시간", "5시간"};

    /** 인터벌 판정: 30초 평균 파워가 FTP 이상인 구간이 60초 이상 */
    private static final double INTERVAL_FTP_RATIO = 1.0;
    private static final int INTERVAL_MIN_SEC = 60;
    private static final int INTERVAL_MERGE_GAP_SEC = 20;
    private static final int INTERVAL_MAX_COUNT = 30;

    private final ActivityCoreRepository coreRepository;
    private final ActivityPointRepository pointRepository;
    private final RiderZoneSettingService zoneSettingService;

    public ActivityAnalyticsDto getAnalytics(Long activityId) {
        ActivityCore core = coreRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("Activity not found: " + activityId));
        List<ActivityPoint> points = pointRepository.findByActivityCoreIdOrderBySeqAsc(activityId);
        RiderZoneSettingDto setting = zoneSettingService.getEffective(core.getUserId());

        Series s = toMovingSeries(points);
        ActivityAnalyticsDto.ActivityAnalyticsDtoBuilder dto = ActivityAnalyticsDto.builder()
                .zoneSetting(setting)
                .hrZones(s.hasHr ? zoneTimes(s.hr, setting.getMaxHr(), HR_ZONE_PCT, HR_ZONE_NAMES) : List.of())
                .streams(toStreams(points));

        if (!s.hasPower) {
            return dto.powerZones(List.of()).powerCurve(List.of()).intervals(List.of()).build();
        }

        int ftp = setting.getFtp();
        int n = s.size();
        long sum = 0;
        for (int i = 0; i < n; i++) sum += s.power[i];
        double avg = n > 0 ? (double) sum / n : 0;
        Double np = normalizedPower(s.power, n);
        double weight = zoneSettingService.getLatestWeight(core.getUserId());

        dto.avgPower((int) Math.round(avg))
                .workKj((int) Math.round(sum / 1000.0))
                .wattsPerKg(weight > 0 ? round2(avg / weight) : null)
                .powerZones(zoneTimes(s.power, ftp, POWER_ZONE_PCT, POWER_ZONE_NAMES))
                .powerCurve(powerCurve(s.power, n))
                .intervals(intervals(s, ftp));

        if (np != null && ftp > 0) {
            double intensity = np / ftp;
            dto.normalizedPower((int) Math.round(np))
                    .intensityFactor(round2(intensity))
                    .trainingStress((int) Math.round(n * np * intensity / (ftp * 3600.0) * 100))
                    .variabilityIndex(avg > 0 ? round2(np / avg) : null);
        }
        return dto.build();
    }

    // ── 1초 단위 이동 시계열 ─────────────────────────────────────────

    private static class Series {
        int[] power;
        Integer[] hr;
        Integer[] cadence;
        double[] km;
        int length;
        boolean hasPower;
        boolean hasHr;

        int size() {
            return length;
        }
    }

    private Series toMovingSeries(List<ActivityPoint> points) {
        List<int[]> rows = new ArrayList<>();           // {power, hr(-1=없음), cadence(-1=없음)}
        List<Double> kms = new ArrayList<>();
        boolean hasPower = false, hasHr = false;

        for (int i = 0; i < points.size(); i++) {
            ActivityPoint p = points.get(i);
            if (p.getPointTime() == null) continue;

            double power = p.getPower() != null ? p.getPower() : 0;
            double speed = p.getSpeed() != null ? p.getSpeed() : 0;
            if (speed < 1 && power <= 0) continue;      // 정지

            long gap = 1;
            if (i + 1 < points.size() && points.get(i + 1).getPointTime() != null) {
                gap = Duration.between(p.getPointTime(), points.get(i + 1).getPointTime()).getSeconds();
            }
            if (gap <= 0) continue;
            int fill = gap <= MAX_FILL_GAP_SEC ? (int) gap : 1;

            int[] row = {
                    (int) Math.round(power),
                    p.getHeartRate() != null ? (int) Math.round(p.getHeartRate()) : -1,
                    p.getCadence() != null ? (int) Math.round(p.getCadence()) : -1
            };
            if (power > 0) hasPower = true;
            if (row[1] > 0) hasHr = true;
            double km = p.getDistance() != null ? p.getDistance() / 1000.0 : 0;
            for (int f = 0; f < fill; f++) {
                rows.add(row);
                kms.add(km);
            }
        }

        Series s = new Series();
        s.length = rows.size();
        s.power = new int[s.length];
        s.hr = new Integer[s.length];
        s.cadence = new Integer[s.length];
        s.km = new double[s.length];
        for (int i = 0; i < s.length; i++) {
            int[] r = rows.get(i);
            s.power[i] = r[0];
            s.hr[i] = r[1] > 0 ? r[1] : null;
            s.cadence[i] = r[2] >= 0 ? r[2] : null;
            s.km[i] = kms.get(i);
        }
        s.hasPower = hasPower;
        s.hasHr = hasHr;
        return s;
    }

    // ── 존 ──────────────────────────────────────────────────────────

    private List<ZoneTime> zoneTimes(int[] values, int base, int[] pct, String[] names) {
        Integer[] boxed = new Integer[values.length];
        for (int i = 0; i < values.length; i++) boxed[i] = values[i];
        return zoneTimes(boxed, base, pct, names);
    }

    private List<ZoneTime> zoneTimes(Integer[] values, int base, int[] pct, String[] names) {
        int[] bounds = new int[pct.length];
        for (int z = 0; z < pct.length; z++) bounds[z] = (int) Math.round(base * pct[z] / 100.0);

        int[] seconds = new int[pct.length];
        int total = 0;
        for (Integer v : values) {
            if (v == null) continue;
            int z = pct.length - 1;
            while (z > 0 && v < bounds[z]) z--;
            seconds[z]++;
            total++;
        }

        List<ZoneTime> zones = new ArrayList<>();
        for (int z = 0; z < pct.length; z++) {
            zones.add(ZoneTime.builder()
                    .zone("Z" + (z + 1))
                    .name(names[z])
                    .min(bounds[z])
                    .max(z + 1 < pct.length ? bounds[z + 1] - 1 : null)
                    .seconds(seconds[z])
                    .percent(total > 0 ? Math.round(seconds[z] * 1000.0 / total) / 10.0 : 0)
                    .build());
        }
        return zones;
    }

    private String powerZoneOf(double watts, int ftp) {
        int z = POWER_ZONE_PCT.length - 1;
        while (z > 0 && watts < ftp * POWER_ZONE_PCT[z] / 100.0) z--;
        return "Z" + (z + 1);
    }

    // ── 파워 지표 ───────────────────────────────────────────────────

    private Double normalizedPower(int[] power, int n) {
        if (n < ROLLING_SEC) return null;
        double[] rolling = rollingAverage(power, n);
        double sum4 = 0;
        int count = 0;
        for (int i = ROLLING_SEC - 1; i < n; i++) {
            sum4 += Math.pow(rolling[i], 4);
            count++;
        }
        return Math.pow(sum4 / count, 0.25);
    }

    /** 끝점 기준 30초 이동 평균 (앞쪽 29초는 가능한 만큼만 평균) */
    private double[] rollingAverage(int[] power, int n) {
        double[] out = new double[n];
        long window = 0;
        for (int i = 0; i < n; i++) {
            window += power[i];
            if (i >= ROLLING_SEC) window -= power[i - ROLLING_SEC];
            out[i] = (double) window / Math.min(i + 1, ROLLING_SEC);
        }
        return out;
    }

    private List<CurvePoint> powerCurve(int[] power, int n) {
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + power[i];

        List<CurvePoint> curve = new ArrayList<>();
        for (int c = 0; c < CURVE_SECONDS.length; c++) {
            int d = CURVE_SECONDS[c];
            if (d > n) break;
            long best = 0;
            for (int i = d; i <= n; i++) best = Math.max(best, prefix[i] - prefix[i - d]);
            curve.add(CurvePoint.builder()
                    .seconds(d)
                    .label(CURVE_LABELS[c])
                    .watts((int) Math.round((double) best / d))
                    .build());
        }
        return curve;
    }

    private List<Interval> intervals(Series s, int ftp) {
        int n = s.size();
        if (ftp <= 0 || n < INTERVAL_MIN_SEC) return List.of();
        double[] rolling = rollingAverage(s.power, n);
        double threshold = ftp * INTERVAL_FTP_RATIO;

        // 30초 평균이 기준 이상인 구간 찾기 (짧은 끊김은 이어붙임)
        List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= n; i++) {
            boolean above = i < n && rolling[i] >= threshold;
            if (above && start < 0) start = i;
            if (!above && start >= 0) {
                int end = i - 1;
                if (!runs.isEmpty() && start - runs.get(runs.size() - 1)[1] <= INTERVAL_MERGE_GAP_SEC) {
                    runs.get(runs.size() - 1)[1] = end;
                } else {
                    runs.add(new int[]{start, end});
                }
                start = -1;
            }
        }

        List<Interval> result = new ArrayList<>();
        int lag = ROLLING_SEC / 2;  // 끝점 기준 이동평균이라 실제 노력 구간은 약 15초 앞선다
        for (int[] run : runs) {
            int from = Math.max(0, run[0] - lag);
            int to = Math.max(from, run[1] - lag);
            int duration = to - from + 1;
            if (duration < INTERVAL_MIN_SEC) continue;

            long sumP = 0, sumHr = 0, sumCad = 0;
            int maxP = 0, hrCount = 0, cadCount = 0;
            for (int i = from; i <= to; i++) {
                sumP += s.power[i];
                maxP = Math.max(maxP, s.power[i]);
                if (s.hr[i] != null) { sumHr += s.hr[i]; hrCount++; }
                if (s.cadence[i] != null && s.cadence[i] > 0) { sumCad += s.cadence[i]; cadCount++; }
            }
            double avgP = (double) sumP / duration;
            result.add(Interval.builder()
                    .startKm(Math.round(s.km[from] * 10) / 10.0)
                    .durationSec(duration)
                    .avgPower((int) Math.round(avgP))
                    .maxPower(maxP)
                    .avgHr(hrCount > 0 ? (int) Math.round((double) sumHr / hrCount) : null)
                    .avgCadence(cadCount > 0 ? (int) Math.round((double) sumCad / cadCount) : null)
                    .zone(powerZoneOf(avgP, ftp))
                    .build());
            if (result.size() >= INTERVAL_MAX_COUNT) break;
        }
        return result;
    }

    // ── 그래프용 시계열 (거리 구간 평균) ─────────────────────────────

    private List<StreamPoint> toStreams(List<ActivityPoint> points) {
        double totalM = 0;
        for (ActivityPoint p : points) {
            if (p.getDistance() != null) totalM = Math.max(totalM, p.getDistance());
        }
        if (totalM <= 0) return List.of();

        int buckets = Math.min(STREAM_BUCKETS, points.size());
        double[] distSum = new double[buckets], spdSum = new double[buckets], pwrSum = new double[buckets],
                cadSum = new double[buckets], hrSum = new double[buckets], altSum = new double[buckets];
        int[] distN = new int[buckets], movN = new int[buckets], pwrN = new int[buckets],
                cadN = new int[buckets], hrN = new int[buckets], altN = new int[buckets];

        for (ActivityPoint p : points) {
            if (p.getDistance() == null) continue;
            int b = Math.min(buckets - 1, (int) (p.getDistance() / totalM * buckets));
            distSum[b] += p.getDistance();
            distN[b]++;
            if (p.getAltitude() != null) { altSum[b] += p.getAltitude(); altN[b]++; }

            double speed = p.getSpeed() != null ? p.getSpeed() : 0;
            boolean moving = speed >= 1 || (p.getPower() != null && p.getPower() > 0);
            if (!moving) continue;
            spdSum[b] += speed;
            movN[b]++;
            if (p.getPower() != null) { pwrSum[b] += p.getPower(); pwrN[b]++; }
            if (p.getCadence() != null) { cadSum[b] += p.getCadence(); cadN[b]++; }
            if (p.getHeartRate() != null && p.getHeartRate() > 0) { hrSum[b] += p.getHeartRate(); hrN[b]++; }
        }

        List<StreamPoint> out = new ArrayList<>();
        for (int b = 0; b < buckets; b++) {
            if (distN[b] == 0) continue;
            out.add(StreamPoint.builder()
                    .km(Math.round(distSum[b] / distN[b] / 10.0) / 100.0)
                    .speed(movN[b] > 0 ? Math.round(spdSum[b] / movN[b] * 10) / 10.0 : null)
                    .power(pwrN[b] > 0 ? (int) Math.round(pwrSum[b] / pwrN[b]) : null)
                    .cadence(cadN[b] > 0 ? (int) Math.round(cadSum[b] / cadN[b]) : null)
                    .hr(hrN[b] > 0 ? (int) Math.round(hrSum[b] / hrN[b]) : null)
                    .altitude(altN[b] > 0 ? Math.round(altSum[b] / altN[b] * 10) / 10.0 : null)
                    .build());
        }
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

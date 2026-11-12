package com.lifemetrics.backend.power;

import com.lifemetrics.backend.dto.RiderZoneSettingDto;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.service.RiderZoneSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 파워미터 기록으로 파워 커브·라이더 성향·피로 저항을 만든다.
 * <p>
 * 파워미터를 쓰기 시작한 날({@code power.meter-since}) 이후 라이딩만 쓴다. 그 전 파워 값은 앱 추정치다.
 * 라이딩별 계산 결과는 메모리에 캐시한다(활동 id·이동시간·거리가 같으면 재사용).
 */
@Slf4j
@Service
public class PowerProfileService {

    public static final int[] DURATIONS = {5, 15, 30, 60, 120, 300, 600, 1200, 1800, 3600, 7200, 10800, 18000};
    static final int RECENT_DAYS = 90;
    static final int BAND_KJ = 1000;
    static final int BANDS = 5;
    static final int LONG_RIDE_HOURS = 4;
    static final int HIST_BIN_W = 10;
    static final int HIST_BINS = 2500 / HIST_BIN_W + 1;
    /** Coggan 파워 존 하한(FTP 대비) — Z1 은 0 부터 */
    static final double[] ZONE_PCT = {0.56, 0.76, 0.91, 1.06, 1.21, 1.51};
    static final String[][] ZONE_NAMES = {
            {"Z1", "회복"}, {"Z2", "지구력"}, {"Z3", "템포"}, {"Z4", "역치"},
            {"Z5", "VO2max"}, {"Z6", "무산소"}, {"Z7", "신경근"}};
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ActivityCoreRepository activityRepo;
    private final RiderZoneSettingService zoneService;
    private final NamedParameterJdbcTemplate jdbc;
    private final LocalDate meterSince;
    private final Map<String, RideStats> cache = new ConcurrentHashMap<>();

    public PowerProfileService(ActivityCoreRepository activityRepo,
                               RiderZoneSettingService zoneService,
                               NamedParameterJdbcTemplate jdbc,
                               @Value("${power.meter-since:2026-03-21}") String meterSince) {
        this.activityRepo = activityRepo;
        this.zoneService = zoneService;
        this.jdbc = jdbc;
        this.meterSince = LocalDate.parse(meterSince);
    }

    // ── 라이딩 1회 ───────────────────────────────────

    record RideStats(long activityId, LocalDate date, String name, double km, long seconds, long joules, int np,
                     int[] best, int[] band5m, int[] band20m, int[] histogram, List<int[]> hourly) {}

    private RideStats stats(ActivityCore a) {
        String key = a.getId() + ":" + a.getMovingTime() + ":" + a.getTotalDistance();
        return cache.computeIfAbsent(key, k -> compute(a));
    }

    private RideStats compute(ActivityCore a) {
        List<PowerCalculator.Sample> samples = jdbc.query(
                "SELECT point_time, power, heart_rate FROM activity_point WHERE activity_core_id = :id ORDER BY seq",
                Map.of("id", a.getId()),
                (rs, i) -> {
                    Timestamp t = rs.getTimestamp(1);
                    return new PowerCalculator.Sample(t == null ? null : t.toLocalDateTime(),
                            (Double) rs.getObject(2, Double.class), (Double) rs.getObject(3, Double.class));
                });
        List<PowerCalculator.Segment> segs = PowerCalculator.toSegments(samples);
        int[] hist = new int[HIST_BINS];
        for (PowerCalculator.Segment s : segs) for (int v : s.power()) hist[Math.min(HIST_BINS - 1, v / HIST_BIN_W)]++;
        return new RideStats(a.getId(), a.getStartTime().toLocalDate(), a.getName(),
                a.getTotalDistance() == null ? 0 : a.getTotalDistance() / 1000,
                PowerCalculator.totalSeconds(segs), PowerCalculator.totalJoules(segs),
                PowerCalculator.normalizedPower(segs),
                PowerCalculator.bestAverages(segs, DURATIONS),
                PowerCalculator.bestByWorkBand(segs, 300, BAND_KJ, BANDS),
                PowerCalculator.bestByWorkBand(segs, 1200, BAND_KJ, BANDS),
                hist, PowerCalculator.hourly(segs));
    }

    List<RideStats> rides(long userId, LocalDate today) {
        List<RideStats> out = new ArrayList<>();
        for (ActivityCore a : activityRepo.findByUserIdAndDateRange(userId, meterSince.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            if (a.getAvgPower() == null || a.getAvgPower() <= 0 || a.getStartTime() == null) continue;
            try {
                RideStats s = stats(a);
                if (s.seconds() > 0 && s.joules() > 0) out.add(s);
            } catch (Exception e) {
                log.warn("파워 계산 실패 activity={}: {}", a.getId(), e.getMessage());
            }
        }
        out.sort(Comparator.comparing(RideStats::date));
        return out;
    }

    // ── 프로필 ───────────────────────────────────────

    public record CurvePoint(int seconds, String label, int watts, double wkg, Long activityId, String date, String name,
                             int recentWatts) {}

    public record ZoneTime(String zone, String label, int fromW, Integer toW, long seconds, double pct) {}

    public record WorkBand(String label, int fromKj, int best5m, int best20m) {}

    public record HourPoint(int hour, int power, Integer hr) {}

    public record LongRide(long activityId, String name, String date, double km, double hours, int np, int kj,
                           Double fadePct, Integer hrDriftPct, List<HourPoint> hourly) {}

    public record Durability(List<WorkBand> bands, Integer retention5mPct, Integer retention20mPct,
                             Double avgFadePct, List<LongRide> longRides) {}

    public record Profile(String meterSince, int rideCount, double totalHours, double totalKm,
                          double weightKg, int ftpSetting, boolean ftpSettingEstimated, int estimatedFtp,
                          int recentEstimatedFtp, List<CurvePoint> curve,
                          RiderTypeClassifier.Result riderType, List<ZoneTime> zones, Durability durability) {}

    public Profile profile(long userId) {
        LocalDate today = LocalDate.now(KST);
        List<RideStats> rides = rides(userId, today);
        double weight = zoneService.getLatestWeight(userId);
        RiderZoneSettingDto setting = zoneService.getEffective(userId);
        LocalDate recentFrom = today.minusDays(RECENT_DAYS);

        List<CurvePoint> curve = new ArrayList<>();
        int[] bestAll = new int[DURATIONS.length];
        for (int d = 0; d < DURATIONS.length; d++) {
            RideStats bestRide = null;
            int recent = 0;
            for (RideStats r : rides) {
                if (bestRide == null || r.best()[d] > bestRide.best()[d]) bestRide = r;
                if (!r.date().isBefore(recentFrom)) recent = Math.max(recent, r.best()[d]);
            }
            int w = bestRide == null ? 0 : bestRide.best()[d];
            bestAll[d] = w;
            if (w == 0) continue;
            curve.add(new CurvePoint(DURATIONS[d], label(DURATIONS[d]), w, round2(w / weight),
                    bestRide.activityId(), bestRide.date().toString(), bestRide.name(), recent));
        }
        int best20 = bestAll[indexOf(1200)];
        int eFtp = (int) Math.round(best20 * 0.95);
        int recent20 = curve.stream().filter(c -> c.seconds() == 1200).mapToInt(CurvePoint::recentWatts).findFirst().orElse(0);

        double[] wkg = {bestAll[indexOf(5)] / weight, bestAll[indexOf(60)] / weight, bestAll[indexOf(300)] / weight, eFtp / weight};
        RiderTypeClassifier.Result type = RiderTypeClassifier.classify(wkg);

        int ftp = setting.getFtp();
        return new Profile(meterSince.toString(), rides.size(),
                round1(rides.stream().mapToLong(RideStats::seconds).sum() / 3600.0),
                round1(rides.stream().mapToDouble(RideStats::km).sum()),
                round1(weight), ftp, setting.isFtpEstimated(), eFtp, (int) Math.round(recent20 * 0.95),
                curve, type, zones(rides, ftp), durability(rides, bestAll));
    }

    static List<ZoneTime> zones(List<RideStats> rides, int ftp) {
        long[] secs = new long[ZONE_PCT.length + 1];
        for (RideStats r : rides) {
            for (int b = 0; b < r.histogram().length; b++) {
                int w = b * HIST_BIN_W;
                int z = 0;
                while (z < ZONE_PCT.length && w >= ZONE_PCT[z] * ftp) z++;
                secs[z] += r.histogram()[b];
            }
        }
        long total = java.util.Arrays.stream(secs).sum();
        List<ZoneTime> out = new ArrayList<>();
        for (int z = 0; z < secs.length; z++) {
            int from = z == 0 ? 0 : (int) Math.round(ZONE_PCT[z - 1] * ftp);
            Integer to = z == ZONE_PCT.length ? null : (int) Math.round(ZONE_PCT[z] * ftp) - 1;
            out.add(new ZoneTime(ZONE_NAMES[z][0], ZONE_NAMES[z][1], from, to, secs[z],
                    total == 0 ? 0 : round1(secs[z] * 100.0 / total)));
        }
        return out;
    }

    static Durability durability(List<RideStats> rides, int[] bestAll) {
        List<WorkBand> bands = new ArrayList<>();
        int[] b5 = new int[BANDS], b20 = new int[BANDS];
        for (RideStats r : rides) {
            for (int b = 0; b < BANDS; b++) {
                b5[b] = Math.max(b5[b], r.band5m()[b]);
                b20[b] = Math.max(b20[b], r.band20m()[b]);
            }
        }
        for (int b = 0; b < BANDS; b++) {
            String label = b == BANDS - 1 ? (b * BAND_KJ) + "kJ 이후" : (b * BAND_KJ) + "~" + ((b + 1) * BAND_KJ) + "kJ";
            bands.add(new WorkBand(label, b * BAND_KJ, b5[b], b20[b]));
        }
        // 2000kJ 이후 최고 / 전체 최고
        int late5 = 0, late20 = 0;
        for (int b = 2; b < BANDS; b++) {
            late5 = Math.max(late5, b5[b]);
            late20 = Math.max(late20, b20[b]);
        }
        int all5 = bestAll[indexOf(300)], all20 = bestAll[indexOf(1200)];
        Integer ret5 = late5 > 0 && all5 > 0 ? (int) Math.round(late5 * 100.0 / all5) : null;
        Integer ret20 = late20 > 0 && all20 > 0 ? (int) Math.round(late20 * 100.0 / all20) : null;

        List<LongRide> longRides = new ArrayList<>();
        for (RideStats r : rides) {
            if (r.hourly().size() < LONG_RIDE_HOURS) continue;
            List<HourPoint> hp = new ArrayList<>();
            for (int h = 0; h < r.hourly().size(); h++) {
                int[] v = r.hourly().get(h);
                hp.add(new HourPoint(h + 1, v[0], v[1] > 0 ? v[1] : null));
            }
            longRides.add(new LongRide(r.activityId(), r.name(), r.date().toString(), round1(r.km()),
                    round1(r.seconds() / 3600.0), r.np(), (int) (r.joules() / 1000),
                    fadePct(r.hourly()), hrDriftPct(r.hourly()), hp));
        }
        longRides.sort(Comparator.comparing(LongRide::date).reversed());
        Double avgFade = longRides.stream().map(LongRide::fadePct).filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().stream().map(PowerProfileService::round1).boxed().findFirst().orElse(null);
        return new Durability(bands, ret5, ret20, avgFade, longRides);
    }

    /** 처음 1/4 시간 대비 마지막 1/4 시간 평균 파워 변화(%). 음수면 떨어진 것. */
    static Double fadePct(List<int[]> hourly) {
        int n = hourly.size();
        if (n < 2) return null;
        int q = Math.max(1, (int) Math.ceil(n / 4.0));
        double first = hourly.subList(0, q).stream().mapToInt(v -> v[0]).average().orElse(0);
        double last = hourly.subList(n - q, n).stream().mapToInt(v -> v[0]).average().orElse(0);
        return first <= 0 ? null : round1((last - first) * 100 / first);
    }

    /**
     * 심박 대비 파워(효율)의 처음 1/4 대비 마지막 1/4 변화를 심박 쪽 드리프트로 표시(%).
     * 양수면 같은 파워에 심박이 더 올라간 것(피로·탈수). 심박이 없으면 null.
     */
    static Integer hrDriftPct(List<int[]> hourly) {
        int n = hourly.size();
        if (n < 2) return null;
        int q = Math.max(1, (int) Math.ceil(n / 4.0));
        double e1 = efficiency(hourly.subList(0, q)), e2 = efficiency(hourly.subList(n - q, n));
        if (e1 <= 0 || e2 <= 0) return null;
        return (int) Math.round((e1 / e2 - 1) * 100);
    }

    private static double efficiency(List<int[]> hours) {
        double p = 0, h = 0;
        for (int[] v : hours) {
            if (v[1] <= 0) return 0;
            p += v[0];
            h += v[1];
        }
        return h == 0 ? 0 : p / h;
    }

    // ── 형식 ─────────────────────────────────────────

    static int indexOf(int sec) {
        for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == sec) return i;
        throw new IllegalArgumentException(String.valueOf(sec));
    }

    static String label(int sec) {
        if (sec < 60) return sec + "초";
        if (sec < 3600) return (sec / 60) + "분";
        return (sec / 3600) + "시간";
    }

    static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** 애슐리트 챗용 요약 몇 줄 */
    public List<String> summaryLines(long userId) {
        Profile p = profile(userId);
        if (p.rideCount() == 0) return List.of();
        List<String> l = new ArrayList<>();
        l.add("파워미터 사용 " + p.meterSince() + "~, 집계 라이딩 " + p.rideCount() + "회 " + p.totalHours() + "시간 " + p.totalKm() + "km, 체중 " + p.weightKg() + "kg 기준");
        StringBuilder c = new StringBuilder("최고 평균 파워(전체 / 최근 " + RECENT_DAYS + "일): ");
        List<String> parts = new ArrayList<>();
        for (CurvePoint cp : p.curve()) {
            parts.add(cp.label() + " " + cp.watts() + "W(" + cp.wkg() + "W/kg) / " + cp.recentWatts() + "W");
        }
        l.add(c.append(String.join(", ", parts)).toString());
        l.add("추정 FTP(20분×0.95) " + p.estimatedFtp() + "W, 최근 " + RECENT_DAYS + "일 " + p.recentEstimatedFtp() + "W, 설정 FTP " + p.ftpSetting() + "W");
        RiderTypeClassifier.Result t = p.riderType();
        List<String> sc = t.scores().stream().map(s -> s.label() + " " + s.wkg() + "W/kg→" + s.score() + "점").toList();
        l.add("라이더 성향: " + t.label() + " — " + t.description() + " (" + String.join(", ", sc)
                + "; 최대 노력 테스트 기록이 없으면 짧은 구간 점수가 낮게 나온다)");
        Durability d = p.durability();
        if (d.retention5mPct() != null || d.avgFadePct() != null) {
            l.add("피로 저항: 2000kJ 이후 최고 5분 파워가 전체 최고의 " + d.retention5mPct() + "%, 20분 " + d.retention20mPct()
                    + "%; 4시간 이상 라이딩 평균 후반 파워 변화 " + d.avgFadePct() + "%");
        }
        for (LongRide r : d.longRides().stream().limit(5).toList()) {
            l.add("  " + r.date() + " " + (r.name() == null ? "" : r.name()) + " " + r.km() + "km " + r.hours() + "h, NP " + r.np()
                    + "W, " + r.kj() + "kJ, 후반 파워 변화 " + r.fadePct() + "%" + (r.hrDriftPct() == null ? "" : ", 심박 드리프트 " + r.hrDriftPct() + "%"));
        }
        List<String> z = p.zones().stream().map(zt -> zt.zone() + " " + zt.pct() + "%").toList();
        l.add("파워 존 시간 분포(설정 FTP 기준, 코스팅 포함): " + String.join(", ", z));
        return l;
    }
}

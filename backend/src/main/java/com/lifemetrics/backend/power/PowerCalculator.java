package com.lifemetrics.backend.power;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 라이딩 1회분 파워(1초 간격)를 계산하는 순수 함수 모음.
 * <p>
 * 포인트 간격이 {@link #MAX_GAP_SEC}초 이하면 그 사이 초를 현재 값으로 채우고(가민 스마트 기록·센서 끊김),
 * 그보다 길면 정지로 보고 구간을 나눈다. 구간을 넘는 창(window)은 만들지 않는다.
 */
public final class PowerCalculator {

    static final int MAX_GAP_SEC = 10;

    private PowerCalculator() {}

    public record Sample(LocalDateTime time, Double power, Double heartRate) {}

    /** 1초 간격 연속 구간. hr 은 값이 없으면 0. */
    public record Segment(int[] power, int[] hr) {
        int length() { return power.length; }
    }

    public static List<Segment> toSegments(List<Sample> samples) {
        List<Segment> out = new ArrayList<>();
        List<Integer> p = new ArrayList<>();
        List<Integer> h = new ArrayList<>();
        LocalDateTime prev = null;
        for (Sample s : samples) {
            if (s.time() == null) continue;
            int pw = s.power() == null || s.power() < 0 ? 0 : (int) Math.round(Math.min(s.power(), 2500));
            int hr = s.heartRate() == null || s.heartRate() < 30 ? 0 : (int) Math.round(s.heartRate());
            if (prev == null) {
                p.add(pw);
                h.add(hr);
            } else {
                long gap = ChronoUnit.SECONDS.between(prev, s.time());
                if (gap <= 0) continue;
                if (gap > MAX_GAP_SEC) {
                    flush(out, p, h);
                    p.add(pw);
                    h.add(hr);
                } else {
                    for (int i = 0; i < gap; i++) {
                        p.add(pw);
                        h.add(hr);
                    }
                }
            }
            prev = s.time();
        }
        flush(out, p, h);
        return out;
    }

    private static void flush(List<Segment> out, List<Integer> p, List<Integer> h) {
        if (!p.isEmpty()) {
            out.add(new Segment(p.stream().mapToInt(Integer::intValue).toArray(),
                    h.stream().mapToInt(Integer::intValue).toArray()));
        }
        p.clear();
        h.clear();
    }

    /** 구간들을 이어 붙인 누적합 (길이 n+1). 구간 경계 정보는 starts 로 따로 둔다. */
    private record Joined(long[] prefix, int[] segEnd) {}

    private static Joined join(List<Segment> segs) {
        int n = segs.stream().mapToInt(Segment::length).sum();
        long[] prefix = new long[n + 1];
        int[] segEnd = new int[n]; // 각 초가 속한 구간의 끝(배타) 인덱스
        int k = 0;
        for (Segment s : segs) {
            int end = k + s.length();
            for (int v : s.power()) {
                prefix[k + 1] = prefix[k] + v;
                segEnd[k] = end;
                k++;
            }
        }
        return new Joined(prefix, segEnd);
    }

    /** duration 초 동안의 최고 평균 파워. 그만큼 긴 구간이 없으면 0. */
    public static int bestAverage(List<Segment> segs, int duration) {
        Joined j = join(segs);
        return bestAverage(j, duration);
    }

    private static int bestAverage(Joined j, int duration) {
        long best = -1;
        int n = j.segEnd.length;
        for (int i = 0; i + duration <= n; i++) {
            if (i + duration > j.segEnd[i]) continue;
            long sum = j.prefix[i + duration] - j.prefix[i];
            if (sum > best) best = sum;
        }
        return best < 0 ? 0 : (int) Math.round((double) best / duration);
    }

    public static int[] bestAverages(List<Segment> segs, int[] durations) {
        Joined j = join(segs);
        int[] out = new int[durations.length];
        for (int d = 0; d < durations.length; d++) out[d] = bestAverage(j, durations[d]);
        return out;
    }

    /**
     * 누적 일(kJ) 구간별 최고 평균 파워. 창이 시작할 때까지 한 일로 구간을 나눈다.
     * 결과[b] = 시작 시점 누적 일이 bandKj*b 이상 bandKj*(b+1) 미만인 창 중 최고 (마지막 구간은 그 이상 전부). 없으면 0.
     */
    public static int[] bestByWorkBand(List<Segment> segs, int duration, int bandKj, int bands) {
        Joined j = join(segs);
        long[] best = new long[bands];
        java.util.Arrays.fill(best, -1);
        int n = j.segEnd.length;
        for (int i = 0; i + duration <= n; i++) {
            if (i + duration > j.segEnd[i]) continue;
            int band = (int) Math.min(bands - 1, j.prefix[i] / 1000 / bandKj);
            long sum = j.prefix[i + duration] - j.prefix[i];
            if (sum > best[band]) best[band] = sum;
        }
        int[] out = new int[bands];
        for (int b = 0; b < bands; b++) out[b] = best[b] < 0 ? 0 : (int) Math.round((double) best[b] / duration);
        return out;
    }

    /** 1시간(움직인 시간) 단위 평균 파워·평균 심박. 마지막 시간은 30분 이상일 때만. 심박이 없으면 0. */
    public static List<int[]> hourly(List<Segment> segs) {
        List<int[]> out = new ArrayList<>();
        long pSum = 0, hSum = 0;
        int count = 0, hCount = 0;
        for (Segment s : segs) {
            for (int i = 0; i < s.length(); i++) {
                pSum += s.power()[i];
                if (s.hr()[i] > 0) {
                    hSum += s.hr()[i];
                    hCount++;
                }
                if (++count == 3600) {
                    out.add(new int[]{(int) Math.round((double) pSum / count), hCount == 0 ? 0 : (int) Math.round((double) hSum / hCount)});
                    pSum = hSum = 0;
                    count = hCount = 0;
                }
            }
        }
        if (count >= 1800) {
            out.add(new int[]{(int) Math.round((double) pSum / count), hCount == 0 ? 0 : (int) Math.round((double) hSum / hCount)});
        }
        return out;
    }

    /** 노멀라이즈드 파워: 30초 이동평균의 4제곱 평균의 4제곱근. */
    public static int normalizedPower(List<Segment> segs) {
        double sum4 = 0;
        long count = 0;
        for (Segment s : segs) {
            if (s.length() < 30) continue;
            long roll = 0;
            for (int i = 0; i < s.length(); i++) {
                roll += s.power()[i];
                if (i >= 30) roll -= s.power()[i - 30];
                if (i >= 29) {
                    sum4 += Math.pow(roll / 30.0, 4);
                    count++;
                }
            }
        }
        return count == 0 ? 0 : (int) Math.round(Math.pow(sum4 / count, 0.25));
    }

    /** 존 경계(파워 하한, 오름차순)별 머문 초. 결과 길이 = bounds.length + 1 (첫 칸은 첫 경계 미만). */
    public static long[] timeInZones(List<Segment> segs, double[] bounds) {
        long[] out = new long[bounds.length + 1];
        for (Segment s : segs) {
            for (int v : s.power()) {
                int z = 0;
                while (z < bounds.length && v >= bounds[z]) z++;
                out[z]++;
            }
        }
        return out;
    }

    public static long totalSeconds(List<Segment> segs) {
        return segs.stream().mapToLong(Segment::length).sum();
    }

    public static long totalJoules(List<Segment> segs) {
        long j = 0;
        for (Segment s : segs) for (int v : s.power()) j += v;
        return j;
    }
}

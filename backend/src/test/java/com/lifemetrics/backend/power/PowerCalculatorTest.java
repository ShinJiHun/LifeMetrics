package com.lifemetrics.backend.power;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PowerCalculatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 5, 2, 6, 0);

    private static List<PowerCalculator.Sample> seq(int startSec, int... watts) {
        List<PowerCalculator.Sample> out = new ArrayList<>();
        for (int i = 0; i < watts.length; i++) out.add(new PowerCalculator.Sample(T0.plusSeconds(startSec + i), (double) watts[i], 140.0));
        return out;
    }

    @Test
    void 짧은_틈은_채우고_긴_틈은_구간을_나눈다() {
        List<PowerCalculator.Sample> s = new ArrayList<>(seq(0, 100, 200));
        s.add(new PowerCalculator.Sample(T0.plusSeconds(4), 300.0, null)); // 3초 틈 → 300 으로 3칸
        s.addAll(seq(100, 50)); // 96초 틈 → 새 구간
        var segs = PowerCalculator.toSegments(s);
        assertThat(segs).hasSize(2);
        assertThat(segs.get(0).power()).containsExactly(100, 200, 300, 300, 300);
        assertThat(segs.get(1).power()).containsExactly(50);
    }

    @Test
    void 최고_평균은_정지_시간을_빼고_이어서_본다() {
        List<PowerCalculator.Sample> s = new ArrayList<>(seq(0, 100, 400));
        s.addAll(seq(100, 400, 100)); // 98초 정지 후 재개
        var segs = PowerCalculator.toSegments(s);
        assertThat(PowerCalculator.bestAverage(segs, 2)).isEqualTo(400);
        assertThat(PowerCalculator.bestAverage(segs, 4)).isEqualTo(250);
        assertThat(PowerCalculator.bestAverage(segs, 5)).isZero();
    }

    @Test
    void 누적_일_구간별_최고() {
        // 1000W × 1초씩: 시작 시점 누적 일이 0,1,2,3kJ
        var segs = PowerCalculator.toSegments(seq(0, 1000, 1000, 1000, 500));
        int[] b = PowerCalculator.bestByWorkBand(segs, 1, 1, 3);
        assertThat(b).containsExactly(1000, 1000, 1000); // 마지막 구간은 2kJ 이후 전부
    }

    @Test
    void 일정한_파워의_NP_는_평균과_같다() {
        int[] w = new int[120];
        java.util.Arrays.fill(w, 200);
        var segs = PowerCalculator.toSegments(seq(0, w));
        assertThat(PowerCalculator.normalizedPower(segs)).isEqualTo(200);
    }

    @Test
    void 시간별_평균과_후반_변화() {
        int[] w = new int[3600 * 4];
        for (int i = 0; i < w.length; i++) w[i] = i < 3600 ? 200 : (i >= 3600 * 3 ? 150 : 180);
        var hourly = PowerCalculator.hourly(PowerCalculator.toSegments(seq(0, w)));
        assertThat(hourly).hasSize(4);
        assertThat(hourly.get(0)[0]).isEqualTo(200);
        assertThat(PowerProfileService.fadePct(hourly)).isEqualTo(-25.0);
        assertThat(PowerProfileService.hrDriftPct(hourly)).isEqualTo(33);
    }

    @Test
    void 성향_판정() {
        // 5초 10.5, 1분 6, 5분 4.5, FTP 3.9 W/kg → FTP 축이 상대적으로 가장 높다
        var r = RiderTypeClassifier.classify(new double[]{10.5, 6.0, 4.5, 3.9});
        assertThat(r.type()).isEqualTo("tt");
        var sprinter = RiderTypeClassifier.classify(new double[]{22, 7, 3.5, 2.8});
        assertThat(sprinter.type()).isEqualTo("sprinter");
        assertThat(RiderTypeClassifier.classify(new double[]{0, 6, 4, 3}).type()).isEqualTo("unknown");
    }
}

package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.BikePart;
import com.lifemetrics.backend.repository.BikeRideDistance;
import com.lifemetrics.backend.repository.GearDistanceRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BikePartServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    private static BikeRideDistance ride(LocalDate date, double km) {
        return new BikeRideDistance() {
            public Long getBikeId() { return 1L; }
            public LocalDateTime getStartTime() { return date.atTime(7, 0); }
            public Double getTotalDistance() { return km * 1000; }
        };
    }

    private static BikePart part(LocalDate installed, LocalDate removed, Integer intervalKm, Integer months) {
        BikePart p = new BikePart();
        p.setId(1L);
        p.setBikeId(1L);
        p.setPartType("CHAIN");
        p.setInstalledOn(installed);
        p.setRemovedOn(removed);
        p.setIntervalKm(intervalKm);
        p.setIntervalMonths(months);
        p.setInitialKm(100.0);
        return p;
    }

    @Test
    void 장착일부터_탈거일_전날까지만_거리에_포함() {
        List<BikeRideDistance> rides = List.of(
                ride(LocalDate.of(2026, 9, 30), 500),  // 장착 전
                ride(LocalDate.of(2026, 10, 1), 1000), // 장착일 포함
                ride(LocalDate.of(2026, 10, 20), 1000),
                ride(LocalDate.of(2026, 11, 1), 700)); // 탈거일 제외
        var u = BikePartService.usage(part(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), 3000, null),
                rides, TODAY, 0);
        assertThat(u.usedKm()).isEqualTo(2100.0); // 100 + 1000 + 1000
        assertThat(u.status()).isEqualTo("REMOVED");
        assertThat(u.expectedReplaceOn()).isNull();
    }

    @Test
    void 거리_80퍼센트_이상이면_곧교체_예상일은_최근_주행속도로() {
        List<BikeRideDistance> rides = List.of(ride(LocalDate.of(2026, 10, 1), 2400));
        var u = BikePartService.usage(part(LocalDate.of(2026, 9, 1), null, 3000, null), rides, TODAY, 10);
        assertThat(u.usedKm()).isEqualTo(2500.0);
        assertThat(u.status()).isEqualTo("SOON");
        assertThat(u.remainingKm()).isEqualTo(500.0);
        assertThat(u.expectedReplaceOn()).isEqualTo(TODAY.plusDays(50));
    }

    @Test
    void 기간_주기를_넘기면_교체필요() {
        var u = BikePartService.usage(part(LocalDate.of(2025, 10, 1), null, null, 12), List.of(), TODAY, 0);
        assertThat(u.status()).isEqualTo("OVERDUE");
        assertThat(u.expectedReplaceOn()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void 주기가_없으면_NONE() {
        var u = BikePartService.usage(part(LocalDate.of(2026, 1, 1), null, null, null), List.of(), TODAY, 5);
        assertThat(u.status()).isEqualTo("NONE");
        assertThat(u.ratio()).isNull();
    }

    private static GearDistanceRow gear(LocalDate date, int front, int rear, double km) {
        return new GearDistanceRow() {
            public Long getBikeId() { return 1L; }
            public LocalDateTime getRideStart() { return date.atTime(7, 0); }
            public Integer getFrontGear() { return front; }
            public Integer getRearGear() { return rear; }
            public Double getDistance() { return km * 1000; }
        };
    }

    @Test
    void 톱니수별_거리는_장착일_이후만_1km_미만은_제외() {
        List<GearDistanceRow> rows = List.of(
                gear(LocalDate.of(2026, 5, 1), 50, 17, 300),  // 카세트 장착 전
                gear(LocalDate.of(2026, 6, 1), 50, 17, 100),
                gear(LocalDate.of(2026, 6, 2), 34, 17, 50),
                gear(LocalDate.of(2026, 6, 2), 34, 28, 0.4)); // 센서 튐
        var rear = BikePartService.gearKm(rows, LocalDate.of(2026, 6, 1), false);
        assertThat(rear).containsExactly(new BikePartService.GearKm(17, 150.0));
        var front = BikePartService.gearKm(rows, null, true);
        assertThat(front).containsExactly(new BikePartService.GearKm(34, 50.4), new BikePartService.GearKm(50, 400.0));
    }

    @Test
    void 최근_90일_평균_주행거리() {
        List<BikeRideDistance> rides = List.of(
                ride(TODAY.minusDays(10), 450),
                ride(TODAY.minusDays(200), 9999));
        assertThat(BikePartService.recentKmPerDay(rides, TODAY)).isEqualTo(5.0);
    }
}

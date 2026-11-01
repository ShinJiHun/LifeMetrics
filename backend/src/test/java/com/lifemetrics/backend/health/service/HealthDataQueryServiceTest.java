package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.service.HealthDataQueryService.DateRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthDataQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Test
    void 기간을_생략하면_오늘까지_최근_30일이다() {
        assertThat(HealthDataQueryService.resolveRange(null, null, TODAY))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 3), TODAY));
    }

    @Test
    void from_만_주면_거기부터_30일이다() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertThat(HealthDataQueryService.resolveRange(from, null, TODAY))
                .isEqualTo(new DateRange(from, LocalDate.of(2026, 1, 30)));
    }

    @Test
    void to_만_주면_거기까지_30일이다() {
        LocalDate to = LocalDate.of(2026, 1, 30);
        assertThat(HealthDataQueryService.resolveRange(null, to, TODAY))
                .isEqualTo(new DateRange(LocalDate.of(2026, 1, 1), to));
    }

    @Test
    void from_이_to_보다_늦으면_거부한다() {
        assertThatThrownBy(() -> HealthDataQueryService.resolveRange(TODAY, TODAY.minusDays(1), TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기간은_양끝_포함_400일까지다() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        assertThat(HealthDataQueryService.resolveRange(from, from.plusDays(399), TODAY).to())
                .isEqualTo(from.plusDays(399));
        assertThatThrownBy(() -> HealthDataQueryService.resolveRange(from, from.plusDays(400), TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

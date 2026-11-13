package com.lifemetrics.backend.persona.handler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HandlerTripServiceTest {

    private static HandlerTripService.Draft draft(String date, String start, String end) {
        return new HandlerTripService.Draft(date, start, end, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    @Test
    void hhmm_normalizes() {
        assertThat(HandlerTripService.hhmm("9:05")).isEqualTo("09:05");
        assertThat(HandlerTripService.hhmm("21:30:00")).isEqualTo("21:30");
        assertThat(HandlerTripService.hhmm("오후 없음")).isNull();
        assertThat(HandlerTripService.hhmm("25:00")).isNull();
    }

    @Test
    void endBeforeStart_crossesMidnight() {
        var d = draft("2026-11-10", "23:40", "00:25");
        LocalDateTime start = HandlerTripService.startAt(d);
        assertThat(start).isEqualTo(LocalDateTime.of(2026, 11, 10, 23, 40));
        assertThat(HandlerTripService.endAt(d, start)).isEqualTo(LocalDateTime.of(2026, 11, 11, 0, 25));
    }

    @Test
    void withYear_forcesChosenYear() {
        assertThat(HandlerTripService.withYear("2026-03-05", 2025)).isEqualTo("2025-03-05");
        assertThat(HandlerTripService.withYear("11.10", 2025)).isEqualTo("2025-11-10");
        assertThat(HandlerTripService.withYear("11월 10일", 2024)).isEqualTo("2024-11-10");
        assertThat(HandlerTripService.withYear("2025-02-29", 2024)).isEqualTo("2024-02-29");
        assertThat(HandlerTripService.withYear("02-30", 2025)).isNull();
        assertThat(HandlerTripService.withYear(null, 2025)).isNull();
    }

    @Test
    void dateFormats() {
        assertThat(HandlerTripService.parseDate("2026.11.10")).hasToString("2026-11-10");
        assertThat(HandlerTripService.parseDate("모름")).isNull();
        assertThat(HandlerTripService.startAt(draft("2026-11-10", null, null))).isNull();
    }
}

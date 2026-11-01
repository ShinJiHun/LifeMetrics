package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.dto.HealthSyncRequest;
import com.lifemetrics.backend.health.dto.HealthSyncRequest.Day;
import com.lifemetrics.backend.health.dto.HealthSyncRequest.Exercise;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthSyncServiceTest {

    @Test
    void 자정_직전_UTC_시각은_KST_다음날로_저장된다() {
        LocalDateTime kst = HealthSyncService.toKst(OffsetDateTime.parse("2026-10-31T16:30:00Z"));
        assertThat(kst).isEqualTo(LocalDateTime.of(2026, 11, 1, 1, 30));
    }

    @Test
    void 식사_종류는_HealthConnect_표기도_받아들이고_모르는_값은_unknown() {
        assertThat(HealthSyncService.normalizeMealType("MEAL_TYPE_LUNCH")).isEqualTo("lunch");
        assertThat(HealthSyncService.normalizeMealType(" Dinner ")).isEqualTo("dinner");
        assertThat(HealthSyncService.normalizeMealType("brunch")).isEqualTo("unknown");
        assertThat(HealthSyncService.normalizeMealType(null)).isEqualTo("unknown");
    }

    @Test
    void 빈_묶음은_통과한다() {
        HealthSyncService.validate(new HealthSyncRequest("s24", null, null, null, null));
    }

    @Test
    void 같은_날짜가_두_번_오면_거부한다() {
        LocalDate d = LocalDate.of(2026, 11, 1);
        var req = new HealthSyncRequest("s24",
                List.of(new Day(d, 1L, null, null, null, null, null, null),
                        new Day(d, 2L, null, null, null, null, null, null)),
                null, null, null);
        assertThatThrownBy(() -> HealthSyncService.validate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같은 날짜");
    }

    @Test
    void 운동에_종료시각이_없으면_거부한다() {
        var req = new HealthSyncRequest("s24", null, null,
                List.of(new Exercise("ex-1", "BIKING", null,
                        OffsetDateTime.parse("2026-11-01T07:00:00+09:00"), null, null, null, null, null)),
                null);
        assertThatThrownBy(() -> HealthSyncService.validate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime");
    }

    @Test
    void 레코드가_너무_많으면_나눠_보내라고_거부한다() {
        var ids = Collections.nCopies(HealthSyncService.MAX_RECORDS + 1, "x");
        var req = new HealthSyncRequest("s24", null, null, null, ids);
        assertThatThrownBy(() -> HealthSyncService.validate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("나눠서");
    }
}

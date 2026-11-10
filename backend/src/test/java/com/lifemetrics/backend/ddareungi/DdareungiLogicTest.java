package com.lifemetrics.backend.ddareungi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DdareungiLogicTest {

    @Test
    void splitsStationNumberFromName() {
        assertThat(DdareungiStationService.splitNo("102. 망원역 1번출구 앞"))
                .containsExactly("102", "망원역 1번출구 앞");
        assertThat(DdareungiStationService.splitNo("2219 고속터미널역 8-1번"))
                .containsExactly("2219", "고속터미널역 8-1번");
        assertThat(DdareungiStationService.splitNo("여의나루역 1번출구 앞"))
                .containsExactly(null, "여의나루역 1번출구 앞");
    }

    @Test
    void metFollowsSpeedBands() {
        assertThat(DdareungiImportService.met(null)).isEqualTo(4.0);
        assertThat(DdareungiImportService.met(12.0)).isEqualTo(4.0);
        assertThat(DdareungiImportService.met(17.0)).isEqualTo(6.8);
        assertThat(DdareungiImportService.met(21.0)).isEqualTo(8.0);
        assertThat(DdareungiImportService.met(25.0)).isEqualTo(10.0);
    }

    @Test
    void haversineBetweenNearbyStations() {
        // 망원역 1번출구 ↔ 합정역 1번출구 (서울시 bikeList 좌표) ≈ 0.75km
        double d = DdareungiImportService.haversineM(37.55564880, 126.91062927, 37.55073929, 126.91508484);
        assertThat(d).isBetween(600.0, 800.0);
    }
}

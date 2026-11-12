package com.lifemetrics.backend.persona.handler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoRouteServiceTest {

    @Test
    void regionOf() {
        assertThat(KakaoRouteService.regionOf("경기 구리시 인창동 239-3")).isEqualTo("구리시 인창동");
        assertThat(KakaoRouteService.regionOf("경기도 남양주시 와부읍 덕소리 110-1")).isEqualTo("남양주시 와부읍 덕소리");
        assertThat(KakaoRouteService.regionOf("서울 동대문구 전농동 601-2 에스앤제이프리미안")).isEqualTo("동대문구 전농동");
        assertThat(KakaoRouteService.regionOf("서울특별시 강북구 도봉로 123")).isEqualTo("강북구 도봉로");
        assertThat(KakaoRouteService.regionOf(null)).isNull();
    }
}

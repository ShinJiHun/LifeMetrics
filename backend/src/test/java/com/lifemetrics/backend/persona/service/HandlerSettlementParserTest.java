package com.lifemetrics.backend.persona.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HandlerSettlementParserTest {

    private static final String SAMPLE = """
            핸들러 운행료 월간 명세서
            *귀속월 : 2026  년 10월
            운행료 지급정보
            개시일 2026-10-01
            종료일 2026-10-31
            용역제공일수 25일 (58 회)
            용역구분 탁송
            지급금액 571,500원
            위와 같이 지급하였음을 증명합니다.
            2026년 11월 10일
            주식회사 쏘카
            """;

    @Test
    void parsesSocarMonthlyStatement() {
        var p = HandlerSettlementParser.parseText(SAMPLE);
        assertThat(p.ok()).isTrue();
        assertThat(p.periodMonth()).isEqualTo("2026-10");
        assertThat(p.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(p.endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(p.workDays()).isEqualTo(25);
        assertThat(p.trips()).isEqualTo(58);
        assertThat(p.serviceType()).isEqualTo("탁송");
        assertThat(p.grossAmount()).isEqualTo(571_500L);
        assertThat(p.issuedDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void singleDigitMonthIsZeroPadded() {
        var p = HandlerSettlementParser.parseText("*귀속월 : 2026  년 1월\n지급금액 36,750원");
        assertThat(p.periodMonth()).isEqualTo("2026-01");
        assertThat(p.grossAmount()).isEqualTo(36_750L);
    }

    @Test
    void unknownTextIsNotOk() {
        var p = HandlerSettlementParser.parseText("아무 내용");
        assertThat(p.ok()).isFalse();
        assertThat(p.periodMonth()).isNull();
    }
}

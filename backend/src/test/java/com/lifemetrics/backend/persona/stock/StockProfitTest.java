package com.lifemetrics.backend.persona.stock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockProfitTest {

    private static StockService.FlowRow row(String cur, String type, String amt) {
        return new StockService.FlowRow(cur, type, false, new BigDecimal(amt), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static StockService.AccountSnapshot snap(String evalKrw, String evalUsd) {
        BigDecimal z = BigDecimal.ZERO;
        return new StockService.AccountSnapshot(1, LocalDateTime.now(), LocalDate.now(), "KR_CLOSE", "1",
                z, z, new BigDecimal(evalKrw), new BigDecimal(evalUsd), z, z, z, z, z, z, z, z, z, 0);
    }

    @Test
    void 입금_환전_매수_매도_출금을_모두_합쳐_총수익을_낸다() {
        // 100만원 입금 → 50만원을 $400 로 환전 → $400 매수 → $500 에 매도 → $500 를 70만원으로 환전 → 120만원 출금
        var rows = List.of(
                row("KRW", "DEPOSIT", "1000000"),
                row("KRW", "EXCHANGE_OUT", "500000"),
                row("USD", "EXCHANGE_IN", "400"),
                row("USD", "BUY", "400"),
                row("USD", "SELL", "500"),
                row("USD", "EXCHANGE_OUT", "500"),
                row("KRW", "EXCHANGE_IN", "700000"),
                row("KRW", "WITHDRAWAL", "1200000"),
                row("USD", "SECURITIES_TRANSFER_IN", "0"));
        // 남은 보유: 원화 주식 평가 10만원 + 달러 주식 $10 (환율 1400)
        var p = StockService.compute(rows, snap("100000", "10"), new BigDecimal("1400"), LocalDate.now(),
                null, null, rows.size());

        // 현금: KRW 1,000,000 − 500,000 + 700,000 − 1,200,000 = 0, USD 400 − 400 + 500 − 500 = 0
        assertThat(p.cashKrw()).isEqualByComparingTo("0");
        assertThat(p.netDepositKrw()).isEqualByComparingTo("-200000");
        assertThat(p.holdingsTotalKrw()).isEqualByComparingTo("114000");
        // 총 수익 = 114,000 − (−200,000)
        assertThat(p.totalProfitKrw()).isEqualByComparingTo("314000");
        assertThat(p.unknownTypes()).isEmpty();
    }

    @Test
    void 모르는_유형은_계산에서_빼고_알려준다() {
        var p = StockService.compute(List.of(row("KRW", "DEPOSIT", "1000"), row("KRW", "MYSTERY", "50")),
                null, null, null, null, null, 2);
        assertThat(p.cashKrw()).isEqualByComparingTo("1000");
        assertThat(p.totalProfitKrw()).isEqualByComparingTo("0");
        assertThat(p.unknownTypes()).containsExactly("MYSTERY");
    }
}

package com.lifemetrics.backend.persona.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandlerSettlementControllerTest {

    @Test
    void withholdingIsThreePointThreePercentTruncatedToTenWon() {
        long income = HandlerSettlementController.incomeTax(571_500); // 17,145 → 17,140
        long local = HandlerSettlementController.localTax(income);     // 1,714 → 1,710
        assertThat(income).isEqualTo(17_140);
        assertThat(local).isEqualTo(1_710);
        assertThat(571_500 - income - local).isEqualTo(552_650);
    }

    @Test
    void safeBaseNameTrimsBracketUnderscores() {
        assertThat(HandlerSettlementController.safeBaseName("[핸들러] 2026년 10월 운행료 지급명세서 (1).pdf"))
                .isEqualTo("핸들러_ 2026년 10월 운행료 지급명세서 _1");
        assertThat(HandlerSettlementController.safeBaseName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(HandlerSettlementController.safeBaseName("...")).isEqualTo("정산서");
    }
}

package com.lifemetrics.backend.lotto.dto;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class LottoRoundDto {

    private final int roundNo;
    private final LocalDate drawDate;

    public LottoRoundDto(int roundNo, LocalDate drawDate) {
        this.roundNo = roundNo;
        this.drawDate = drawDate;
    }
}

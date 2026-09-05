package com.lifemetrics.backend.lotto.dto;

import lombok.Getter;

@Getter
public class LottoNumberFrequencyDto {
    private final int number;
    private final int count;

    public LottoNumberFrequencyDto(int number, int count) {
        this.number = number;
        this.count = count;
    }
}

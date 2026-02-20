package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import lombok.Getter;

@Getter
public class LottoWinningDto {

    private final int n1, n2, n3, n4, n5, n6, bonus;

    public LottoWinningDto(LottoNumberEntity e) {
        this.n1 = e.getN1();
        this.n2 = e.getN2();
        this.n3 = e.getN3();
        this.n4 = e.getN4();
        this.n5 = e.getN5();
        this.n6 = e.getN6();
        this.bonus = e.getBonus();
    }
}

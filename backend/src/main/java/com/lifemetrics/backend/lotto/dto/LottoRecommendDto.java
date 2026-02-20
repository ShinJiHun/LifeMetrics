package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoRecommendEntity;
import lombok.Getter;

@Getter
public class LottoRecommendDto {

    private final int gameNo;
    private final int num1;
    private final int num2;
    private final int num3;
    private final int num4;
    private final int num5;
    private final int num6;

    public LottoRecommendDto(LottoRecommendEntity e) {
        this.gameNo = e.getGameNo();
        this.num1 = e.getN1();
        this.num2 = e.getN2();
        this.num3 = e.getN3();
        this.num4 = e.getN4();
        this.num5 = e.getN5();
        this.num6 = e.getN6();
    }
}

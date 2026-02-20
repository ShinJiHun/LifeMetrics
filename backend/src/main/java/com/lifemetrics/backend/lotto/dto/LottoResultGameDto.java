package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoRecommendEntity;
import lombok.Getter;

import java.util.Set;

@Getter
public class LottoResultGameDto {

    private final int gameNo;
    private final int n1, n2, n3, n4, n5, n6;
    private final int matchCount;

    public LottoResultGameDto(LottoRecommendEntity e, Set<Integer> winNums) {
        this.gameNo = e.getGameNo();
        this.n1 = e.getN1();
        this.n2 = e.getN2();
        this.n3 = e.getN3();
        this.n4 = e.getN4();
        this.n5 = e.getN5();
        this.n6 = e.getN6();

        this.matchCount = (int) Set.of(
                n1, n2, n3, n4, n5, n6
        ).stream().filter(winNums::contains).count();
    }
}

package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoRecommendEntity;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class LottoRecommendResultDto {

    private final int gameNo;
    private final int matchCount;
    private final boolean bonusMatch;

    private LottoRecommendResultDto(
            int gameNo,
            int matchCount,
            boolean bonusMatch
    ) {
        this.gameNo = gameNo;
        this.matchCount = matchCount;
        this.bonusMatch = bonusMatch;
    }

    public static LottoRecommendResultDto of(
            LottoRecommendEntity rec,
            int[] winNums,
            int bonus
    ) {
        Set<Integer> winSet =
                Arrays.stream(winNums).boxed().collect(Collectors.toSet());

        int[] recNums = {
            rec.getN1(), rec.getN2(), rec.getN3(),
            rec.getN4(), rec.getN5(), rec.getN6()
        };

        int match = 0;
        for (int n : recNums) {
            if (winSet.contains(n)) match++;
        }

        boolean bonusMatch =
                match == 5 && Arrays.stream(recNums).anyMatch(n -> n == bonus);

        return new LottoRecommendResultDto(
            rec.getGameNo(),
            match,
            bonusMatch
        );
    }
}

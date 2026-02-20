package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import com.lifemetrics.backend.lotto.entity.LottoRecommendEntity;
import lombok.Getter;

import java.util.List;

@Getter
public class LottoResultResponse {

    private final int round;
    private final int[] winningNumbers;
    private final int bonus;

    private final List<LottoRecommendResultDto> results;

    private LottoResultResponse(
            int round,
            int[] winningNumbers,
            int bonus,
            List<LottoRecommendResultDto> results
    ) {
        this.round = round;
        this.winningNumbers = winningNumbers;
        this.bonus = bonus;
        this.results = results;
    }

    // ✅ Service에서 호출하는 팩토리 메서드
    public static LottoResultResponse of(
            LottoNumberEntity win,
            List<LottoRecommendEntity> recommends
    ) {
        int[] winNums = {
            win.getN1(),
            win.getN2(),
            win.getN3(),
            win.getN4(),
            win.getN5(),
            win.getN6()
        };

        return new LottoResultResponse(
            win.getRound(),
            winNums,
            win.getBonus(),
            recommends.stream()
                .map(r -> LottoRecommendResultDto.of(r, winNums, win.getBonus()))
                .toList()
        );
    }
}

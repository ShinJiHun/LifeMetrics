package com.lifemetrics.backend.lotto.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class LottoCurrentResponse {

    private final int roundNo;
    private final List<LottoRecommendDto> recommendations;

    // 🔒 생성자는 private
    private LottoCurrentResponse(
            int roundNo,
            List<LottoRecommendDto> recommendations
    ) {
        this.roundNo = roundNo;
        this.recommendations = recommendations;
    }

    // ✅ Service에서 쓰는 of() 메서드
    public static LottoCurrentResponse of(
            int roundNo,
            List<LottoRecommendDto> recommendations
    ) {
        return new LottoCurrentResponse(roundNo, recommendations);
    }
}

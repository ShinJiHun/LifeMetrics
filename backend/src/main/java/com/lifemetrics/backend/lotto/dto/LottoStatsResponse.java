package com.lifemetrics.backend.lotto.dto;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class LottoStatsResponse {

    private final int totalRounds;

    /** 번호(1~45) → 당첨 메인번호 출현 횟수. 번호 오름차순. */
    private final List<LottoNumberFrequencyDto> numberFrequency;

    private final List<LottoNumberFrequencyDto> hotNumbers;
    private final List<LottoNumberFrequencyDto> coldNumbers;

    /** "홀:짝" (예: "3:3") → 해당 조합이 나온 회차 수 */
    private final Map<String, Integer> oddEvenDistribution;

    /** "저:고" (1~22:23~45) → 해당 조합이 나온 회차 수 */
    private final Map<String, Integer> lowHighDistribution;

    private final double avgSum;
    private final double avgConsecutivePairCount;

    public LottoStatsResponse(
            int totalRounds,
            List<LottoNumberFrequencyDto> numberFrequency,
            List<LottoNumberFrequencyDto> hotNumbers,
            List<LottoNumberFrequencyDto> coldNumbers,
            Map<String, Integer> oddEvenDistribution,
            Map<String, Integer> lowHighDistribution,
            double avgSum,
            double avgConsecutivePairCount
    ) {
        this.totalRounds = totalRounds;
        this.numberFrequency = numberFrequency;
        this.hotNumbers = hotNumbers;
        this.coldNumbers = coldNumbers;
        this.oddEvenDistribution = oddEvenDistribution;
        this.lowHighDistribution = lowHighDistribution;
        this.avgSum = avgSum;
        this.avgConsecutivePairCount = avgConsecutivePairCount;
    }
}

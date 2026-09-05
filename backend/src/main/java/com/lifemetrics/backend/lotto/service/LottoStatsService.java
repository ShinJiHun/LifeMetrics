package com.lifemetrics.backend.lotto.service;

import com.lifemetrics.backend.lotto.dto.LottoNumberFrequencyDto;
import com.lifemetrics.backend.lotto.dto.LottoStatsResponse;
import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import com.lifemetrics.backend.lotto.repository.LottoNumberRepository;
import com.lifemetrics.backend.lotto.util.LottoStatsUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 회차별 당첨번호 이력을 바탕으로 한 통계/패턴 계산.
 * 번호별 출현빈도, 홀짝/고저 분포, 평균 합계, 연속번호 빈도 등.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
public class LottoStatsService {

    private final LottoNumberRepository numberRepo;

    public LottoStatsResponse getStats() {
        List<LottoNumberEntity> all = numberRepo.findAll();

        int[] freq = new int[46]; // index 1..45
        Map<String, Integer> oddEven = new TreeMap<>();
        Map<String, Integer> lowHigh = new TreeMap<>();
        long sumTotal = 0;
        long consecutiveTotal = 0;

        for (LottoNumberEntity e : all) {
            int[] nums = {e.getN1(), e.getN2(), e.getN3(), e.getN4(), e.getN5(), e.getN6()};
            for (int n : nums) {
                if (n >= 1 && n <= 45) freq[n]++;
            }

            oddEven.merge(LottoStatsUtil.oddEvenLabel(nums), 1, Integer::sum);
            lowHigh.merge(LottoStatsUtil.lowHighLabel(nums), 1, Integer::sum);
            sumTotal += LottoStatsUtil.sum(nums);
            consecutiveTotal += LottoStatsUtil.consecutivePairCount(nums);
        }

        List<LottoNumberFrequencyDto> numberFrequency = new ArrayList<>();
        for (int n = 1; n <= 45; n++) {
            numberFrequency.add(new LottoNumberFrequencyDto(n, freq[n]));
        }

        List<LottoNumberFrequencyDto> byCountDesc = new ArrayList<>(numberFrequency);
        byCountDesc.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));

        List<LottoNumberFrequencyDto> hot = byCountDesc.subList(0, Math.min(10, byCountDesc.size()));
        List<LottoNumberFrequencyDto> cold = new ArrayList<>(byCountDesc)
                .stream()
                .sorted(Comparator.comparingInt(LottoNumberFrequencyDto::getCount))
                .toList();
        cold = cold.subList(0, Math.min(10, cold.size()));

        int totalRounds = all.size();
        double avgSum = totalRounds == 0 ? 0 : (double) sumTotal / totalRounds;
        double avgConsecutive = totalRounds == 0 ? 0 : (double) consecutiveTotal / totalRounds;

        return new LottoStatsResponse(
                totalRounds,
                numberFrequency,
                hot,
                cold,
                oddEven,
                lowHigh,
                avgSum,
                avgConsecutive
        );
    }
}

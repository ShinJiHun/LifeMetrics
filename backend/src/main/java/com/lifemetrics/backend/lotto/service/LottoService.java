package com.lifemetrics.backend.lotto.service;

import com.lifemetrics.backend.lotto.dto.LottoCurrentResponse;
import com.lifemetrics.backend.lotto.dto.LottoResultResponse;
import com.lifemetrics.backend.lotto.dto.LottoRoundDto;
import com.lifemetrics.backend.lotto.dto.LottoRecommendDto;
import com.lifemetrics.backend.lotto.repository.LottoNumberRepository;
import com.lifemetrics.backend.lotto.repository.LottoRecommendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LottoService {

    private final LottoNumberRepository numberRepo;
    private final LottoRecommendRepository recommendRepo;

    public LottoCurrentResponse getCurrentRound() {
        int nextRound = numberRepo.findMaxRound() + 1;

        return LottoCurrentResponse.of(
            nextRound,
            recommendRepo.findByRound(nextRound)
                .stream()
                .map(LottoRecommendDto::new)
                .toList()
        );
    }

    public List<LottoRoundDto> getRoundList() {
        return numberRepo.findAllRounds();
    }

    public LottoResultResponse getRoundResult(int round) {
        var win = numberRepo.findById(round).orElseThrow();

        return LottoResultResponse.of(
            win,
            recommendRepo.findByRound(round)
        );
    }
}

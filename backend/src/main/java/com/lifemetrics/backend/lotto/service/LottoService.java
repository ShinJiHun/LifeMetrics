package com.lifemetrics.backend.lotto.service;

import com.lifemetrics.backend.lotto.dto.LottoCurrentResponse;
import com.lifemetrics.backend.lotto.dto.LottoNumberRowDto;
import com.lifemetrics.backend.lotto.dto.LottoRecommendRowDto;
import com.lifemetrics.backend.lotto.dto.LottoResultResponse;
import com.lifemetrics.backend.lotto.dto.LottoRoundDto;
import com.lifemetrics.backend.lotto.dto.LottoRecommendDto;
import com.lifemetrics.backend.lotto.dto.LottoTicketRowDto;
import com.lifemetrics.backend.lotto.repository.LottoNumberRepository;
import com.lifemetrics.backend.lotto.repository.LottoRecommendRepository;
import com.lifemetrics.backend.lotto.repository.LottoTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
public class LottoService {
    private final LottoNumberRepository numberRepo;
    private final LottoRecommendRepository recommendRepo;
    private final LottoTicketRepository ticketRepo;

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
        // 최신 20개만 반환 (성능 개선)
        return numberRepo.findAllRounds().stream().limit(20).toList();
    }

    public LottoResultResponse getRoundResult(int round) {
        var win = numberRepo.findById(round).orElseThrow();

        return LottoResultResponse.of(
            win,
            recommendRepo.findByRound(round)
        );
    }

    /** 당첨번호 원본 벌크 조회 (round &gt; after, 회차 오름차순, 최대 limit 건). */
    public List<LottoNumberRowDto> getNumberRows(int after, int limit) {
        return numberRepo.findRowsAfter(after, PageRequest.of(0, limit))
            .stream()
            .map(LottoNumberRowDto::new)
            .toList();
    }

    /** 추천번호 원본 벌크 조회 (id &gt; after, id 오름차순, 최대 limit 건). */
    public List<LottoRecommendRowDto> getRecommendRows(long after, int limit) {
        return recommendRepo.findRowsAfter(after, PageRequest.of(0, limit))
            .stream()
            .map(LottoRecommendRowDto::new)
            .toList();
    }

    /** 구매 티켓 원본 벌크 조회 (id &gt; after, id 오름차순, 최대 limit 건). */
    public List<LottoTicketRowDto> getTicketRows(long after, int limit) {
        return ticketRepo.findRowsAfter(after, PageRequest.of(0, limit))
            .stream()
            .map(LottoTicketRowDto::new)
            .toList();
    }
}

package com.lifemetrics.backend.lotto.controller;

import com.lifemetrics.backend.lotto.dto.LottoCurrentResponse;
import com.lifemetrics.backend.lotto.dto.LottoNumberRowDto;
import com.lifemetrics.backend.lotto.dto.LottoRecommendRowDto;
import com.lifemetrics.backend.lotto.dto.LottoResultResponse;
import com.lifemetrics.backend.lotto.dto.LottoRoundDto;
import com.lifemetrics.backend.lotto.dto.LottoStatsResponse;
import com.lifemetrics.backend.lotto.dto.LottoTicketRowDto;
import com.lifemetrics.backend.lotto.service.LottoService;
import com.lifemetrics.backend.lotto.service.LottoStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lotto")
@RequiredArgsConstructor
// LottoController.java
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
public class LottoController {

    private final LottoService lottoService;
    private final LottoStatsService lottoStatsService;

    @GetMapping("/round/current")
    public LottoCurrentResponse current() {
        return lottoService.getCurrentRound();
    }

    @GetMapping("/round/list")
    public List<LottoRoundDto> list() {
        return lottoService.getRoundList();
    }

    /**
     * 당첨번호 원본 벌크 조회 (클라이언트 sqflite 동기화용).
     * round &gt; after 를 회차 오름차순으로 최대 limit 건.
     * 최초 실행: after=0 부터 빈 배열 나올 때까지 페이지네이션.
     * 이후: after=(로컬 최신 회차) 로 새 회차만.
     */
    @GetMapping("/numbers")
    public List<LottoNumberRowDto> numbers(
            @RequestParam(defaultValue = "0") int after,
            @RequestParam(defaultValue = "500") int limit
    ) {
        return lottoService.getNumberRows(after, clampLimit(limit));
    }

    /**
     * 추천번호 원본 벌크 조회 (sqflite 동기화용). id &gt; after 를 id 오름차순.
     * 최초: after=0 부터 빈 배열까지. 이후: after=(로컬 최신 id).
     */
    @GetMapping("/recommends")
    public List<LottoRecommendRowDto> recommends(
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "500") int limit
    ) {
        return lottoService.getRecommendRows(after, clampLimit(limit));
    }

    /**
     * 구매 티켓 원본 벌크 조회 (sqflite 동기화용). id &gt; after 를 id 오름차순.
     * 매칭 계산 없이 원본만 — 클라이언트가 로컬 당첨번호로 채점한다.
     */
    @GetMapping("/ticket/all")
    public List<LottoTicketRowDto> ticketAll(
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "500") int limit
    ) {
        return lottoService.getTicketRows(after, clampLimit(limit));
    }

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 1000);
    }

    @GetMapping("/round/{round}/result")
    public LottoResultResponse result(@PathVariable int round) {
        return lottoService.getRoundResult(round);
    }

    /** 당첨번호 이력 기반 통계/패턴 (번호별 출현빈도, 홀짝/고저 분포, 평균 합계 등). */
    @GetMapping("/stats")
    public LottoStatsResponse stats() {
        return lottoStatsService.getStats();
    }
}

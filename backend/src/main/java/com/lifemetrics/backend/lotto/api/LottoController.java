package com.lifemetrics.backend.lotto.api;

import com.lifemetrics.backend.lotto.dto.LottoCurrentResponse;
import com.lifemetrics.backend.lotto.dto.LottoResultResponse;
import com.lifemetrics.backend.lotto.dto.LottoRoundDto;
import com.lifemetrics.backend.lotto.service.LottoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lotto")
@RequiredArgsConstructor
// LottoController.java
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
public class LottoController {

    private final LottoService lottoService;

    @GetMapping("/round/current")
    public LottoCurrentResponse current() {
        return lottoService.getCurrentRound();
    }

    @GetMapping("/round/list")
    public List<LottoRoundDto> list() {
        return lottoService.getRoundList();
    }

    @GetMapping("/round/{round}/result")
    public LottoResultResponse result(@PathVariable int round) {
        return lottoService.getRoundResult(round);
    }
}

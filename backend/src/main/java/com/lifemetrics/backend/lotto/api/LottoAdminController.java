package com.lifemetrics.backend.lotto.api;

import com.lifemetrics.backend.lotto.dto.LottoSyncResponse;
import com.lifemetrics.backend.lotto.service.LottoSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로또 관리자 전용 엔드포인트. POST 이므로 AdminWriteFilter 가 자동으로 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/lotto/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
public class LottoAdminController {

    private final LottoSyncService lottoSyncService;

    /** 동행복권 공개 API에서 아직 없는 회차의 당첨번호를 가져와 채운다. */
    @PostMapping("/sync")
    public LottoSyncResponse sync() {
        return lottoSyncService.syncAll();
    }
}

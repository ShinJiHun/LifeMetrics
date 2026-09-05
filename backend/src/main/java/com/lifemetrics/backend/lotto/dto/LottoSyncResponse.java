package com.lifemetrics.backend.lotto.dto;

import lombok.Getter;

@Getter
public class LottoSyncResponse {
    private final int fromRound;
    private final int toRound;
    private final int syncedCount;
    private final String message;

    public LottoSyncResponse(int fromRound, int toRound, int syncedCount, String message) {
        this.fromRound = fromRound;
        this.toRound = toRound;
        this.syncedCount = syncedCount;
        this.message = message;
    }
}

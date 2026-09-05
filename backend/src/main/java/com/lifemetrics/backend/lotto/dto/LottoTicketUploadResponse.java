package com.lifemetrics.backend.lotto.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class LottoTicketUploadResponse {

    private final boolean success;
    private final String message;
    private final List<LottoTicketDto> tickets;

    private LottoTicketUploadResponse(boolean success, String message, List<LottoTicketDto> tickets) {
        this.success = success;
        this.message = message;
        this.tickets = tickets;
    }

    public static LottoTicketUploadResponse ok(List<LottoTicketDto> tickets) {
        return new LottoTicketUploadResponse(true, tickets.size() + "게임 저장 완료", tickets);
    }

    public static LottoTicketUploadResponse fail(String message) {
        return new LottoTicketUploadResponse(false, message, List.of());
    }
}

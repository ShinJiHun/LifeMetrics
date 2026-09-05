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
        return ok(tickets, 0);
    }

    /** duplicateCount: 이번 업로드에서 이미 등록돼 있어 새로 저장하지 않고 기존 기록을 반환한 게임 수. */
    public static LottoTicketUploadResponse ok(List<LottoTicketDto> tickets, int duplicateCount) {
        int newCount = tickets.size() - duplicateCount;
        String message = duplicateCount == 0
                ? newCount + "게임 저장 완료"
                : newCount + "게임 저장 완료 (중복 " + duplicateCount + "게임 제외)";
        return new LottoTicketUploadResponse(true, message, tickets);
    }

    /** 용지 전체(모든 게임)가 이미 등록되어 있던 경우. */
    public static LottoTicketUploadResponse duplicate(List<LottoTicketDto> tickets) {
        return new LottoTicketUploadResponse(true, "이미 등록된 용지입니다. 기존 기록을 표시합니다.", tickets);
    }

    public static LottoTicketUploadResponse fail(String message) {
        return new LottoTicketUploadResponse(false, message, List.of());
    }
}

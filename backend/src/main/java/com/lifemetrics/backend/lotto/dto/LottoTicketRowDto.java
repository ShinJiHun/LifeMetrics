package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoTicketEntity;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 구매 티켓 원본 한 행. 클라이언트(Flutter sqflite) 벌크 동기화용.
 * 당첨 매칭은 클라이언트가 로컬 당첨번호로 계산한다(서버 조인 없음).
 * {@code GET /api/lotto/ticket/all} 응답 원소.
 */
@Getter
public class LottoTicketRowDto {

    private final long id;
    private final String ticketGroup;
    private final Integer round;
    private final int gameNo;
    private final int[] numbers;
    private final String source;
    private final String imagePath;
    private final LocalDate purchasedAt;
    private final LocalDateTime issuedAt;
    private final LocalDateTime createdAt;

    public LottoTicketRowDto(LottoTicketEntity e) {
        this.id = e.getId();
        this.ticketGroup = e.getTicketGroup();
        this.round = e.getRound();
        this.gameNo = e.getGameNo();
        this.numbers = e.numbers();
        this.source = e.getSource();
        this.imagePath = e.getImagePath();
        this.purchasedAt = e.getPurchasedAt();
        this.issuedAt = e.getIssuedAt();
        this.createdAt = e.getCreatedAt();
    }
}

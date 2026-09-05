package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoTicketEntity;
import com.lifemetrics.backend.lotto.util.LottoStatsUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class LottoTicketDto {

    private final Long id;
    private final String ticketGroup;
    private final Integer round;
    private final int gameNo;
    private final int[] numbers;
    private final String source;
    private final String imagePath;
    private final LocalDate purchasedAt;
    private final LocalDateTime issuedAt;
    private final LocalDateTime createdAt;

    private final boolean duplicate;

    private final int oddCount;
    private final int evenCount;
    private final int lowCount;
    private final int highCount;
    private final int sum;
    private final int consecutivePairCount;

    /** 이미 추첨된 회차라면 채워지는 당첨 결과 대비 매칭 개수. 아니면 null. */
    private final Integer matchCount;
    private final Boolean bonusMatch;

    public LottoTicketDto(LottoTicketEntity e, Integer matchCount, Boolean bonusMatch, boolean duplicate) {
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
        this.duplicate = duplicate;

        this.oddCount = LottoStatsUtil.oddCount(numbers);
        this.evenCount = LottoStatsUtil.evenCount(numbers);
        this.lowCount = LottoStatsUtil.lowCount(numbers);
        this.highCount = LottoStatsUtil.highCount(numbers);
        this.sum = LottoStatsUtil.sum(numbers);
        this.consecutivePairCount = LottoStatsUtil.consecutivePairCount(numbers);

        this.matchCount = matchCount;
        this.bonusMatch = bonusMatch;
    }

    public LottoTicketDto(LottoTicketEntity e, Integer matchCount, Boolean bonusMatch) {
        this(e, matchCount, bonusMatch, false);
    }

    public LottoTicketDto(LottoTicketEntity e) {
        this(e, null, null, false);
    }
}

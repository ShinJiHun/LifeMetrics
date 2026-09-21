package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoRecommendEntity;
import lombok.Getter;

/**
 * 추천번호 원본 한 행. 클라이언트(Flutter sqflite) 벌크 동기화용.
 * {@code GET /api/lotto/recommends} 응답 원소.
 */
@Getter
public class LottoRecommendRowDto {

    private final long id;
    private final Integer round;
    private final int gameNo;
    private final int num1, num2, num3, num4, num5, num6;

    public LottoRecommendRowDto(LottoRecommendEntity e) {
        this.id = e.getId();
        this.round = e.getRound();
        this.gameNo = e.getGameNo();
        this.num1 = e.getN1();
        this.num2 = e.getN2();
        this.num3 = e.getN3();
        this.num4 = e.getN4();
        this.num5 = e.getN5();
        this.num6 = e.getN6();
    }
}

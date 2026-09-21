package com.lifemetrics.backend.lotto.dto;

import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 당첨번호 원본 한 행. 클라이언트(Flutter sqflite) 벌크 동기화용.
 * {@code GET /api/lotto/numbers} 응답 원소.
 */
@Getter
public class LottoNumberRowDto {

    private final int round;
    private final int num1, num2, num3, num4, num5, num6;
    private final int bonus;
    private final LocalDate drawDate;

    public LottoNumberRowDto(LottoNumberEntity e) {
        this.round = e.getRound();
        this.num1 = e.getN1();
        this.num2 = e.getN2();
        this.num3 = e.getN3();
        this.num4 = e.getN4();
        this.num5 = e.getN5();
        this.num6 = e.getN6();
        this.bonus = e.getBonus();
        this.drawDate = e.getDrawDate();
    }
}

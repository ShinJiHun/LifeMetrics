package com.lifemetrics.backend.health.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 식단 기록 저장/수정 요청. 영양값은 받지 않고 foodCd 와 amount 로 서버에서 계산한다.
 * 수정(PUT)에서는 null 인 항목을 바꾸지 않는다.
 */
@Data
public class FoodLogRequest {
    private LocalDate logDate;
    private String mealType;      // breakfast | lunch | dinner | snack | unknown (기본 unknown)
    private String foodCd;        // 검색 결과의 foodCd
    private String foodName;      // 메뉴명. 없으면 식품명을 쓴다.
    private Double amount;        // 섭취량 g/mL. 없으면 제품 1개 용량을 쓴다.
    private LocalDateTime eatTime;
    private String memo;
}

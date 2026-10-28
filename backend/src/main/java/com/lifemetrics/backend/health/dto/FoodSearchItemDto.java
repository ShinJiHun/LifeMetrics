package com.lifemetrics.backend.health.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 식품 검색 결과 1건. 같은 이름·제조사·용량·영양값의 중복 행은 하나로 합치고
 * {@link #duplicates} 에 합친 개수를 남긴다.
 */
@Data
@Builder
public class FoodSearchItemDto {
    private String foodCd;
    private String foodName;
    private String makerName;
    private String dbClass;

    private Double packageSize;   // 제품 1개 용량 (예: 355)
    private String packageUnit;   // mL | g
    private int duplicates;       // 합쳐진 동일 행 수 (1 = 중복 없음)
    private boolean corrected;    // 원본 오류를 보정한 값인지 (food_nutrition_correction)

    private Nutrients per100;     // 기준량(보통 100g/100mL)당
    private Nutrients perPackage; // 제품 1개당 (packageSize 없으면 null)
    private Nutrients perAmount;  // 요청한 섭취량(amount)당 (amount 없으면 null)

    @Data
    @Builder
    public static class Nutrients {
        private Double amount;    // 이 값들의 기준 양
        private Double calories;  // kcal
        private Double carbs;     // g
        private Double sugar;     // g
        private Double protein;   // g
        private Double fat;       // g
        private Double saturatedFat; // g
        private Double dietaryFiber; // g
        private Double sodium;    // mg
    }
}

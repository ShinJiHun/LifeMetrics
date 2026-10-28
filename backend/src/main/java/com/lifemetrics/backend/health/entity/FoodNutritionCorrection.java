package com.lifemetrics.backend.health.entity;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * food_nutrition_master 원본 오류 보정(읽기 전용).
 * <p>
 * 식약처 원본에 "제품 1개 기준 값이 100g/100mL 칸에 들어간" 행이 있어
 * (예: 칠성사이다 500mL 215kcal/100mL) 영양성분에 {@link #scale} 을 곱해 바로잡는다.
 * data_pipeline 의 detect_food_nutrition_errors.py 가 채우고, master 재적재에도 유지된다.
 */
@Entity
@Table(name = "food_nutrition_correction")
@Getter
public class FoodNutritionCorrection {

    @Id
    @Column(name = "food_cd")
    private String foodCd;

    private Double scale;  // 0.2 = 1/5

    private String source; // auto | manual

    private String reason;
}

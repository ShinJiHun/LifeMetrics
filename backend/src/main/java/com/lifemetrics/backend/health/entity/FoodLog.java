package com.lifemetrics.backend.health.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 식단 기록 1건. 영양값은 저장 시점의 섭취량 기준으로 계산해 고정한다.
 * food_log 의 나머지 영양 컬럼(비타민 등)은 식품영양성분DB에 없어 다루지 않는다.
 */
@Entity
@Table(name = "food_log")
@Getter
@Setter
public class FoodLog {

    public static final String SOURCE_NUTRITION_DB = "food_nutrition_db";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "meal_type", nullable = false)
    private String mealType; // breakfast | lunch | dinner | snack | unknown

    @Column(name = "food_name")
    private String foodName; // 메뉴명

    @Column(name = "food_cd")
    private String foodCd;

    @Column(name = "maker_name")
    private String makerName;

    private Double amount;

    @Column(name = "amount_unit")
    private String amountUnit;

    private Double calories;
    private Double carbs;
    private Double protein;
    private Double fat;
    private Double sugar;
    private Double sodium;

    @Column(name = "saturated_fat")
    private Double saturatedFat;

    @Column(name = "dietary_fiber")
    private Double dietaryFiber;

    @Column(name = "eat_time")
    private LocalDateTime eatTime;

    @Column(name = "data_source")
    private String dataSource;

    @Column(columnDefinition = "TEXT")
    private String memo;
}

package com.lifemetrics.backend.health.entity;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * 식약처 식품영양성분DB 마스터(읽기 전용).
 * <p>
 * data_pipeline 의 load_food_nutrition_master.py 가 공공데이터 API에서 받아 적재한다.
 * 영양성분은 {@link #basisAmount}{@link #basisUnit}(보통 100g/100mL) 기준이고,
 * {@link #foodWeight} 는 제품 1개(캔/병/봉지) 중량이다.
 */
@Entity
@Table(name = "food_nutrition_master")
@Getter
public class FoodNutritionMaster {

    @Id
    @Column(name = "food_cd")
    private String foodCd;

    @Column(name = "food_name")
    private String foodName;

    @Column(name = "db_group")
    private String dbGroup; // 음식, 가공식품, 원재료성 등

    @Column(name = "db_class")
    private String dbClass; // 품목대표, 상용제품 등

    private String category;

    @Column(name = "maker_name")
    private String makerName;

    @Column(name = "basis_amount")
    private Double basisAmount;

    @Column(name = "basis_unit")
    private String basisUnit;

    @Column(name = "food_weight")
    private Double foodWeight;

    @Column(name = "food_weight_unit")
    private String foodWeightUnit;

    // ── 기준량당 영양성분 ─────────────────────────────
    private Double calories;     // kcal
    private Double carbs;        // g
    private Double sugar;        // g
    private Double protein;      // g
    private Double fat;          // g
    @Column(name = "saturated_fat")
    private Double saturatedFat; // g
    @Column(name = "dietary_fiber")
    private Double dietaryFiber; // g
    private Double sodium;       // mg
}

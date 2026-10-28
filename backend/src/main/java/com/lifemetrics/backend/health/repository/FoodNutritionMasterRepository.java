package com.lifemetrics.backend.health.repository;

import com.lifemetrics.backend.health.entity.FoodNutritionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FoodNutritionMasterRepository extends JpaRepository<FoodNutritionMaster, String> {

    /**
     * 식품명 부분일치 검색. 정확일치 → 짧은 이름 → 용량 순으로 정렬해
     * "칠성사이다" 검색 시 "칠성사이다제로그린플럼" 보다 본품이 먼저 오게 한다.
     */
    @Query(value = """
            SELECT * FROM food_nutrition_master
            WHERE food_name LIKE CONCAT('%', :q, '%')
              AND (:maker IS NULL OR maker_name LIKE CONCAT('%', :maker, '%'))
            ORDER BY (food_name = :q) DESC, CHAR_LENGTH(food_name), food_name, food_weight
            LIMIT :limit
            """, nativeQuery = true)
    List<FoodNutritionMaster> searchByName(@Param("q") String q,
                                           @Param("maker") String maker,
                                           @Param("limit") int limit);
}

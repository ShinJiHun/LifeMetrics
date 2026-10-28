package com.lifemetrics.backend.health.repository;

import com.lifemetrics.backend.health.entity.FoodNutritionCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodNutritionCorrectionRepository extends JpaRepository<FoodNutritionCorrection, String> {
}

package com.lifemetrics.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionDto {
    private double calories;    // kcal
    private double protein;     // g
    private double fat;         // g
    private double carbs;       // g
    private double sugar;       // g
    private double fiber;       // g
    private String status;      // success / no_data
}

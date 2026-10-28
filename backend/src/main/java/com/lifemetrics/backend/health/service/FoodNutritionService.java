package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.dto.FoodSearchItemDto;
import com.lifemetrics.backend.health.dto.FoodSearchItemDto.Nutrients;
import com.lifemetrics.backend.health.entity.FoodNutritionMaster;
import com.lifemetrics.backend.health.repository.FoodNutritionMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 식약처 식품영양성분DB 검색.
 * <p>
 * 원본은 같은 제품이 여러 번 등록돼 있는 경우가 많아(예: 칠성사이다 500mL 3건)
 * 이름·제조사·용량·주요 영양값이 같으면 하나로 합친다.
 */
@Service
@RequiredArgsConstructor
public class FoodNutritionService {

    private static final int MAX_LIMIT = 100;

    private final FoodNutritionMasterRepository repository;

    /**
     * @param q      식품명(부분일치)
     * @param maker  제조사(부분일치, 선택)
     * @param size   제품 1개 용량 필터(예: 355, 선택)
     * @param amount 실제 섭취량 g/mL(선택) → perAmount 계산
     */
    @Transactional(readOnly = true)
    public List<FoodSearchItemDto> search(String q, String maker, Double size, Double amount, int limit) {
        int max = Math.min(Math.max(limit, 1), MAX_LIMIT);
        // 중복 제거·용량 필터로 줄어드는 만큼 넉넉히 가져온다.
        List<FoodNutritionMaster> rows = repository.searchByName(q.trim(), blankToNull(maker), max * 4);

        Map<String, FoodSearchItemDto> merged = new LinkedHashMap<>();
        for (FoodNutritionMaster f : rows) {
            if (size != null && !sameSize(f.getFoodWeight(), size)) {
                continue;
            }
            FoodSearchItemDto existing = merged.get(dedupKey(f));
            if (existing != null) {
                existing.setDuplicates(existing.getDuplicates() + 1);
                continue;
            }
            merged.put(dedupKey(f), toDto(f, amount));
        }
        return merged.values().stream().limit(max).toList();
    }

    private FoodSearchItemDto toDto(FoodNutritionMaster f, Double amount) {
        return FoodSearchItemDto.builder()
                .foodCd(f.getFoodCd())
                .foodName(f.getFoodName())
                .makerName(f.getMakerName())
                .dbClass(f.getDbClass())
                .packageSize(f.getFoodWeight())
                .packageUnit(f.getFoodWeightUnit() != null ? f.getFoodWeightUnit() : f.getBasisUnit())
                .duplicates(1)
                .per100(scale(f, basis(f)))
                .perPackage(f.getFoodWeight() != null ? scale(f, f.getFoodWeight()) : null)
                .perAmount(amount != null ? scale(f, amount) : null)
                .build();
    }

    /** 기준량 값을 target 양으로 환산한다. */
    private Nutrients scale(FoodNutritionMaster f, double target) {
        double r = target / basis(f);
        return Nutrients.builder()
                .amount(target)
                .calories(mul(f.getCalories(), r))
                .carbs(mul(f.getCarbs(), r))
                .sugar(mul(f.getSugar(), r))
                .protein(mul(f.getProtein(), r))
                .fat(mul(f.getFat(), r))
                .saturatedFat(mul(f.getSaturatedFat(), r))
                .dietaryFiber(mul(f.getDietaryFiber(), r))
                .sodium(mul(f.getSodium(), r))
                .build();
    }

    private static double basis(FoodNutritionMaster f) {
        return f.getBasisAmount() != null && f.getBasisAmount() > 0 ? f.getBasisAmount() : 100.0;
    }

    private static Double mul(Double v, double r) {
        return v == null ? null : Math.round(v * r * 10) / 10.0;
    }

    private static boolean sameSize(Double weight, double size) {
        return weight != null && Math.abs(weight - size) < 0.5;
    }

    private static String dedupKey(FoodNutritionMaster f) {
        return String.join("|",
                f.getFoodName().replace(" ", ""),
                String.valueOf(f.getMakerName()),
                String.valueOf(f.getFoodWeight()),
                String.valueOf(f.getCalories()),
                String.valueOf(f.getCarbs()),
                String.valueOf(f.getSugar()),
                String.valueOf(f.getProtein()),
                String.valueOf(f.getFat()),
                String.valueOf(f.getSodium()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

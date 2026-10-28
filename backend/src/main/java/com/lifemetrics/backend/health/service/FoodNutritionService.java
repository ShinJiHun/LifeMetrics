package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.dto.FoodSearchItemDto;
import com.lifemetrics.backend.health.dto.FoodSearchItemDto.Nutrients;
import com.lifemetrics.backend.health.entity.FoodNutritionCorrection;
import com.lifemetrics.backend.health.entity.FoodNutritionMaster;
import com.lifemetrics.backend.health.repository.FoodNutritionCorrectionRepository;
import com.lifemetrics.backend.health.repository.FoodNutritionMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 식약처 식품영양성분DB 검색.
 * <p>
 * 원본은 같은 제품이 여러 번 등록돼 있는 경우가 많아(예: 칠성사이다 500mL 3건)
 * 이름·제조사·용량·주요 영양값이 같으면 하나로 합친다.
 * 원본 오류는 food_nutrition_correction 의 배율로 보정한 뒤 합친다.
 */
@Service
@RequiredArgsConstructor
public class FoodNutritionService {

    private static final int MAX_LIMIT = 100;

    private final FoodNutritionMasterRepository repository;
    private final FoodNutritionCorrectionRepository correctionRepository;

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
        Map<String, Double> scales = correctionRepository
                .findAllById(rows.stream().map(FoodNutritionMaster::getFoodCd).toList()).stream()
                .collect(Collectors.toMap(FoodNutritionCorrection::getFoodCd, FoodNutritionCorrection::getScale));

        Map<String, FoodSearchItemDto> merged = new LinkedHashMap<>();
        for (FoodNutritionMaster f : rows) {
            if (size != null && !sameSize(f.getFoodWeight(), size)) {
                continue;
            }
            FoodSearchItemDto dto = toDto(f, scales.get(f.getFoodCd()), amount);
            String key = dedupKey(dto);
            FoodSearchItemDto existing = merged.get(key);
            if (existing != null) {
                existing.setDuplicates(existing.getDuplicates() + 1);
                continue;
            }
            merged.put(key, dto);
        }
        return merged.values().stream().limit(max).toList();
    }

    /** @param correction 원본 오류 보정 배율(없으면 null) */
    private FoodSearchItemDto toDto(FoodNutritionMaster f, Double correction, Double amount) {
        double c = correction != null ? correction : 1.0;
        return FoodSearchItemDto.builder()
                .foodCd(f.getFoodCd())
                .foodName(f.getFoodName())
                .makerName(f.getMakerName())
                .dbClass(f.getDbClass())
                .packageSize(f.getFoodWeight())
                .packageUnit(f.getFoodWeightUnit() != null ? f.getFoodWeightUnit() : f.getBasisUnit())
                .duplicates(1)
                .corrected(correction != null && correction != 1.0)
                .per100(scale(f, basis(f), c))
                .perPackage(f.getFoodWeight() != null ? scale(f, f.getFoodWeight(), c) : null)
                .perAmount(amount != null ? scale(f, amount, c) : null)
                .build();
    }

    /** 기준량 값을 target 양으로 환산한다. */
    private Nutrients scale(FoodNutritionMaster f, double target, double correction) {
        double r = target / basis(f) * correction;
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

    /** 보정 후 값 기준으로 비교해서, 보정된 행과 원래 정상인 행이 같으면 합쳐지게 한다. */
    private static String dedupKey(FoodSearchItemDto d) {
        Nutrients n = d.getPer100();
        return String.join("|",
                d.getFoodName().replace(" ", ""),
                String.valueOf(d.getMakerName()),
                String.valueOf(d.getPackageSize()),
                String.valueOf(n.getCalories()),
                String.valueOf(n.getCarbs()),
                String.valueOf(n.getSugar()),
                String.valueOf(n.getProtein()),
                String.valueOf(n.getFat()),
                String.valueOf(n.getSodium()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

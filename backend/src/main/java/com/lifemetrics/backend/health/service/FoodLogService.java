package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.dto.FoodLogRequest;
import com.lifemetrics.backend.health.dto.FoodSearchItemDto;
import com.lifemetrics.backend.health.dto.FoodSearchItemDto.Nutrients;
import com.lifemetrics.backend.health.entity.FoodLog;
import com.lifemetrics.backend.health.repository.FoodLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * 식품 검색 결과를 메뉴명·섭취량과 함께 food_log 에 저장한다.
 * 쓸 때마다 그날(날짜를 옮기면 이전 날짜도) food_daily_summary 를 다시 계산한다.
 */
@Service
@RequiredArgsConstructor
public class FoodLogService {

    private static final Set<String> MEAL_TYPES = Set.of("breakfast", "lunch", "dinner", "snack", "unknown");

    private final FoodLogRepository repository;
    private final FoodNutritionService nutritionService;
    private final FoodDailySummaryService summaryService;

    @Transactional(readOnly = true)
    public List<FoodLog> list(Long userId, LocalDate date) {
        return repository.findByUserIdAndLogDateOrderByEatTimeAscIdAsc(userId, date);
    }

    @Transactional
    public FoodLog create(Long userId, FoodLogRequest req) {
        if (req.getLogDate() == null) {
            throw new IllegalArgumentException("날짜(logDate)를 입력해 주세요.");
        }
        if (isBlank(req.getFoodCd())) {
            throw new IllegalArgumentException("식품 코드(foodCd)를 입력해 주세요.");
        }
        FoodLog log = new FoodLog();
        log.setUserId(userId);
        log.setLogDate(req.getLogDate());
        log.setMealType(req.getMealType() != null ? validMealType(req.getMealType()) : "unknown");
        log.setEatTime(req.getEatTime());
        log.setMemo(req.getMemo());
        log.setDataSource(FoodLog.SOURCE_NUTRITION_DB);
        applyFood(log, req.getFoodCd(), req.getAmount(), req.getFoodName());
        FoodLog saved = repository.saveAndFlush(log);
        summaryService.recompute(userId, saved.getLogDate());
        return saved;
    }

    @Transactional
    public FoodLog update(Long id, FoodLogRequest req) {
        FoodLog log = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("식단 기록을 찾을 수 없습니다: " + id));
        LocalDate oldDate = log.getLogDate();

        applyUpdate(log, req);

        repository.flush();
        summaryService.recompute(log.getUserId(), log.getLogDate());
        if (!log.getLogDate().equals(oldDate)) {
            summaryService.recompute(log.getUserId(), oldDate);
        }
        return log;
    }

    private void applyUpdate(FoodLog log, FoodLogRequest req) {
        if (req.getLogDate() != null) log.setLogDate(req.getLogDate());
        if (req.getMealType() != null) log.setMealType(validMealType(req.getMealType()));
        if (req.getEatTime() != null) log.setEatTime(req.getEatTime());
        if (req.getMemo() != null) log.setMemo(req.getMemo());

        boolean foodChanged = !isBlank(req.getFoodCd()) && !req.getFoodCd().equals(log.getFoodCd());
        if (foodChanged) {
            applyFood(log, req.getFoodCd(), req.getAmount() != null ? req.getAmount() : log.getAmount(), req.getFoodName());
            return;
        }
        if (req.getAmount() != null) {
            if (log.getFoodCd() == null) {
                throw new IllegalArgumentException("식품 코드가 없는 기록은 섭취량을 바꿀 수 없습니다.");
            }
            applyFood(log, log.getFoodCd(), req.getAmount(), log.getFoodName());
        }
        if (!isBlank(req.getFoodName())) log.setFoodName(req.getFoodName().trim());
    }

    @Transactional
    public void delete(Long id) {
        FoodLog log = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("식단 기록을 찾을 수 없습니다: " + id));
        repository.delete(log);
        repository.flush();
        summaryService.recompute(log.getUserId(), log.getLogDate());
    }

    /** 식품 정보와 섭취량 기준 영양값을 채운다. amount 가 없으면 제품 1개 용량을 쓴다. */
    private void applyFood(FoodLog log, String foodCd, Double amount, String menuName) {
        if (amount != null && amount <= 0) {
            throw new IllegalArgumentException("섭취량(amount)은 0보다 커야 합니다.");
        }
        FoodSearchItemDto food = nutritionService.find(foodCd.trim(), amount)
                .orElseThrow(() -> new IllegalArgumentException("식품 코드를 찾을 수 없습니다: " + foodCd));
        Nutrients n = amount != null ? food.getPerAmount() : food.getPerPackage();
        if (n == null) {
            throw new IllegalArgumentException("제품 용량 정보가 없는 식품입니다. 섭취량(amount)을 입력해 주세요.");
        }
        log.setFoodCd(food.getFoodCd());
        log.setFoodName(!isBlank(menuName) ? menuName.trim() : food.getFoodName());
        log.setMakerName(food.getMakerName());
        log.setAmount(n.getAmount());
        log.setAmountUnit(food.getPackageUnit());
        log.setCalories(n.getCalories());
        log.setCarbs(n.getCarbs());
        log.setProtein(n.getProtein());
        log.setFat(n.getFat());
        log.setSugar(n.getSugar());
        log.setSodium(n.getSodium());
        log.setSaturatedFat(n.getSaturatedFat());
        log.setDietaryFiber(n.getDietaryFiber());
    }

    private static String validMealType(String mealType) {
        if (!MEAL_TYPES.contains(mealType)) {
            throw new IllegalArgumentException("끼니(mealType)는 " + MEAL_TYPES + " 중 하나여야 합니다.");
        }
        return mealType;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.dto.FoodLogRequest;
import com.lifemetrics.backend.health.dto.FoodSearchItemDto;
import com.lifemetrics.backend.health.dto.FoodSearchItemDto.Nutrients;
import com.lifemetrics.backend.health.entity.FoodLog;
import com.lifemetrics.backend.health.repository.FoodLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoodLogServiceTest {

    private final FoodLogRepository repository = mock(FoodLogRepository.class);
    private final FoodNutritionService nutrition = mock(FoodNutritionService.class);
    private FoodLogService service;

    @BeforeEach
    void setUp() {
        service = new FoodLogService(repository, nutrition);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static FoodSearchItemDto food(Double packageSize, Double amount) {
        return FoodSearchItemDto.builder()
                .foodCd("F1").foodName("칠성사이다").makerName("롯데").packageUnit("mL")
                .perPackage(packageSize != null ? Nutrients.builder().amount(packageSize).calories(150.0).build() : null)
                .perAmount(amount != null ? Nutrients.builder().amount(amount).calories(amount * 0.4).build() : null)
                .build();
    }

    private static FoodLogRequest request(Double amount, String menu) {
        FoodLogRequest r = new FoodLogRequest();
        r.setLogDate(LocalDate.of(2026, 11, 10));
        r.setFoodCd("F1");
        r.setAmount(amount);
        r.setFoodName(menu);
        return r;
    }

    @Test
    void 메뉴명과_섭취량_기준_영양값을_저장한다() {
        when(nutrition.find("F1", 200.0)).thenReturn(Optional.of(food(355.0, 200.0)));

        FoodLog log = service.create(1L, request(200.0, "점심 사이다"));

        assertThat(log.getFoodName()).isEqualTo("점심 사이다");
        assertThat(log.getFoodCd()).isEqualTo("F1");
        assertThat(log.getAmount()).isEqualTo(200.0);
        assertThat(log.getCalories()).isEqualTo(80.0);
        assertThat(log.getMealType()).isEqualTo("unknown");
        assertThat(log.getDataSource()).isEqualTo(FoodLog.SOURCE_NUTRITION_DB);
    }

    @Test
    void 섭취량과_메뉴명이_없으면_제품1개와_식품명을_쓴다() {
        when(nutrition.find("F1", null)).thenReturn(Optional.of(food(355.0, null)));

        FoodLog log = service.create(1L, request(null, null));

        assertThat(log.getFoodName()).isEqualTo("칠성사이다");
        assertThat(log.getAmount()).isEqualTo(355.0);
        assertThat(log.getCalories()).isEqualTo(150.0);
    }

    @Test
    void 용량정보도_섭취량도_없으면_거부한다() {
        when(nutrition.find("F1", null)).thenReturn(Optional.of(food(null, null)));

        assertThatThrownBy(() -> service.create(1L, request(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 잘못된_끼니와_섭취량을_거부한다() {
        FoodLogRequest badMeal = request(100.0, null);
        badMeal.setMealType("brunch");
        assertThatThrownBy(() -> service.create(1L, badMeal)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(1L, request(0.0, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 섭취량만_수정하면_메뉴명은_유지하고_영양값을_다시_계산한다() {
        FoodLog existing = new FoodLog();
        existing.setFoodCd("F1");
        existing.setFoodName("점심 사이다");
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(nutrition.find("F1", 100.0)).thenReturn(Optional.of(food(355.0, 100.0)));

        FoodLogRequest req = new FoodLogRequest();
        req.setAmount(100.0);
        FoodLog log = service.update(5L, req);

        assertThat(log.getFoodName()).isEqualTo("점심 사이다");
        assertThat(log.getCalories()).isEqualTo(40.0);
    }

    @Test
    void 식품코드_없는_기록의_섭취량은_바꿀_수_없다() {
        when(repository.findById(5L)).thenReturn(Optional.of(new FoodLog()));
        FoodLogRequest req = new FoodLogRequest();
        req.setAmount(100.0);

        assertThatThrownBy(() -> service.update(5L, req)).isInstanceOf(IllegalArgumentException.class);
    }
}

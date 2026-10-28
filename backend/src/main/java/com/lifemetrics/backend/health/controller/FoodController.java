package com.lifemetrics.backend.health.controller;

import com.lifemetrics.backend.health.dto.FoodSearchItemDto;
import com.lifemetrics.backend.health.service.FoodNutritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 식품 영양성분 검색(식약처 공공데이터, 공개 조회).
 * <p>
 * 예) GET /api/food/search?q=칠성사이다&size=355
 *     GET /api/food/search?q=맛동산&maker=해태&amount=30
 */
@RestController
@RequestMapping("/api/food")
@RequiredArgsConstructor
public class FoodController {

    private final FoodNutritionService service;

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q,
                                    @RequestParam(required = false) String maker,
                                    @RequestParam(required = false) Double size,
                                    @RequestParam(required = false) Double amount,
                                    @RequestParam(defaultValue = "30") int limit) {
        if (q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "검색어(q)를 입력해 주세요."));
        }
        List<FoodSearchItemDto> items = service.search(q, maker, size, amount, limit);
        return ResponseEntity.ok(items);
    }
}

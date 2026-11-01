package com.lifemetrics.backend.health.controller;

import com.lifemetrics.backend.health.dto.FoodLogRequest;
import com.lifemetrics.backend.health.dto.FoodSearchItemDto;
import com.lifemetrics.backend.health.entity.FoodLog;
import com.lifemetrics.backend.health.service.FoodLogService;
import com.lifemetrics.backend.health.service.FoodNutritionService;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 식품 영양성분 검색(식약처 공공데이터, 공개 조회).
 * <p>
 * 예) GET /api/food/search?q=칠성사이다&size=355
 *     GET /api/food/search?q=맛동산&maker=해태&amount=30
 * <p>
 * 식단 기록(/logs)은 관리자 전용이다. 쓰기는 AdminWriteFilter 가 제한하고,
 * 읽기는 기본 공개라 여기서 명시적으로 막는다.
 */
@RestController
@RequestMapping("/api/food")
@RequiredArgsConstructor
public class FoodController {

    private final FoodNutritionService service;
    private final FoodLogService logService;
    private final AdminWriteFilter adminWriteFilter;

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

    @GetMapping("/logs")
    public ResponseEntity<?> listLogs(HttpServletRequest request,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestParam(defaultValue = "1") Long userId) {
        if (!adminWriteFilter.isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        return ResponseEntity.ok(logService.list(userId, date));
    }

    @PostMapping("/logs")
    public ResponseEntity<FoodLog> createLog(@RequestParam(defaultValue = "1") Long userId,
                                             @RequestBody FoodLogRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(logService.create(userId, req));
    }

    @PutMapping("/logs/{id}")
    public FoodLog updateLog(@PathVariable Long id, @RequestBody FoodLogRequest req) {
        return logService.update(id, req);
    }

    @DeleteMapping("/logs/{id}")
    public ResponseEntity<Void> deleteLog(@PathVariable Long id) {
        logService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }
}

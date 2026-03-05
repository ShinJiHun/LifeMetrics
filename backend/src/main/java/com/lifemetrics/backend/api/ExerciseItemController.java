package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.ExerciseHistoryResponse;
import com.lifemetrics.backend.dto.ExerciseLogRequest;
import com.lifemetrics.backend.entity.ExerciseCategory;
import com.lifemetrics.backend.entity.ExerciseItem;
import com.lifemetrics.backend.service.ExerciseCategoryService;
import com.lifemetrics.backend.service.ExerciseItemService;
import com.lifemetrics.backend.service.ExerciseLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exercise")
@RequiredArgsConstructor
public class ExerciseItemController {

    private final ExerciseCategoryService categoryService;
    private final ExerciseItemService itemService;
    private final ExerciseLogService logService;

    @GetMapping("/categories")
    public List<ExerciseCategory> getCategories() {
        return categoryService.findAll();
    }

    @GetMapping("/items")
    public List<ExerciseItem> getItems(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean hasMedia) {

        if (hasMedia != null && hasMedia) {
            return itemService.findByMediaUrlIsNotNull();
        }
        if (categoryId != null) {
            return itemService.findByCategoryId(categoryId);
        }
        return itemService.findAll();
    }

    // 메서드 추가
    @PostMapping("/log")
    public ResponseEntity<String> saveExerciseLog(@RequestBody ExerciseLogRequest request) {
        try {
            logService.saveExerciseLog(request);
            return ResponseEntity.ok("저장 완료");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("저장 실패: " + e.getMessage());
        }
    }
    @GetMapping("/history")
    public List<ExerciseHistoryResponse> getHistory(
            @RequestParam String month,
            @RequestParam Long userId) {
        return logService.getHistory(userId, month);
    }
}
package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.ExerciseHistoryResponse;
import com.lifemetrics.backend.dto.ExerciseItemDto;
import com.lifemetrics.backend.dto.ExerciseLogRequest;
import com.lifemetrics.backend.entity.ExerciseCategory;
import com.lifemetrics.backend.service.ExerciseCategoryService;
import com.lifemetrics.backend.service.ExerciseLogService;
import com.lifemetrics.backend.service.ExerciseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exercise")
@RequiredArgsConstructor
public class ExerciseItemController {

    private final ExerciseCategoryService categoryService;
    private final ExerciseLogService logService;
    private final ExerciseService exerciseService;

    @Value("${exercise.gif.path:/mnt/200gb/NAS/exercise-gifs/}")
    private String gifBasePath;

    // ── 카테고리 목록 ─────────────────────────────────────────────
    @GetMapping("/categories")
    public List<ExerciseCategory> getCategories() {
        return categoryService.findAll();
    }

    // ── 운동 목록 (gifUrl prefix 포함 DTO 반환) ───────────────────
    @GetMapping("/items")
    public List<ExerciseItemDto> getItems(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean hasMedia) {
        return exerciseService.getItems(categoryId, hasMedia);
    }

    // ── 운동 기록 저장 ────────────────────────────────────────────
    @PostMapping("/log")
    public ResponseEntity<String> saveExerciseLog(@RequestBody ExerciseLogRequest request) {
        try {
            logService.saveExerciseLog(request);
            return ResponseEntity.ok("저장 완료");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("저장 실패: " + e.getMessage());
        }
    }

    // ── 운동 기록 조회 ────────────────────────────────────────────
    @GetMapping("/history")
    public List<ExerciseHistoryResponse> getHistory(
            @RequestParam String month,
            @RequestParam Long userId) {
        return logService.getHistory(userId, month);
    }

    // ── GIF 서빙 (서브폴더 경로 지원: /gif/chest/CHEST_PRESS.gif) ─
    @GetMapping("/gif/**")
    public ResponseEntity<Resource> getGif(HttpServletRequest request) {
        String filePath = request.getRequestURI().split("/api/exercise/gif/")[1];
        Path gifPath = Paths.get(gifBasePath, filePath);
        if (!Files.exists(gifPath)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .header("Cache-Control", "public, max-age=86400")
                .body(new FileSystemResource(gifPath));
    }

    // ── GIF 업로드 ────────────────────────────────────────────────
    @PostMapping("/gif/{itemId}")
    public ResponseEntity<Map<String, Object>> uploadGif(
            @PathVariable Long itemId,
            @RequestParam("file") MultipartFile file) {
        return exerciseService.uploadGif(itemId, file);
    }
}

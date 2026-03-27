package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.ExerciseItemDto;
import com.lifemetrics.backend.entity.ExerciseItem;
import com.lifemetrics.backend.repository.ExerciseCategoryRepository;
import com.lifemetrics.backend.repository.ExerciseItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseItemRepository exerciseItemRepository;
    private final ExerciseCategoryRepository exerciseCategoryRepository;

    @Value("${exercise.gif.path:/mnt/200gb/NAS/exercise-gifs/}")
    private String gifBasePath;

    // 로컬: /gif/  → /gif/chest/CHEST_PRESS.gif  (public 폴더 직접)
    // GCP:  /api/exercise/gif/  → /api/exercise/gif/chest/CHEST_PRESS.gif (NAS 서빙)
    @Value("${exercise.gif.base-url:/gif/}")
    private String gifBaseUrl;

    // ── 카테고리 목록 ─────────────────────────────────────────────
    public List<Map<String, Object>> getCategories() {
        return exerciseCategoryRepository.findAll().stream()
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", c.getId());
                    map.put("name", c.getName());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // ── 운동 목록 조회 (DTO 변환 + gifUrl prefix 적용) ────────────
    public List<ExerciseItemDto> getItems(Long categoryId, Boolean hasMedia) {
        List<ExerciseItem> items;

        if (hasMedia != null && hasMedia) {
            items = exerciseItemRepository.findByGifUrlIsNotNullAndIsActiveTrue();
        } else if (categoryId != null) {
            items = exerciseItemRepository.findByCategoryIdAndIsActiveTrue(categoryId);
        } else {
            items = exerciseItemRepository.findByIsActiveTrue();
        }

        return items.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── GIF 파일 서빙 ─────────────────────────────────────────────
    public ResponseEntity<Resource> getGif(String filePath) {
        Path gifPath = Paths.get(gifBasePath, filePath);
        if (!Files.exists(gifPath)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .header("Cache-Control", "public, max-age=86400")
                .body(new FileSystemResource(gifPath));
    }

    // ── GIF 업로드 ────────────────────────────────────────────────
    public ResponseEntity<Map<String, Object>> uploadGif(Long itemId, MultipartFile file) {
        try {
            Path dirPath = Paths.get(gifBasePath);
            Files.createDirectories(dirPath);
            Files.copy(file.getInputStream(), dirPath.resolve(itemId + ".gif"),
                    StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "gifUrl", gifBaseUrl + itemId + ".gif",
                    "message", "GIF 업로드 완료"
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "업로드 실패: " + e.getMessage()
            ));
        }
    }

    // ── DTO 변환 ──────────────────────────────────────────────────
    private ExerciseItemDto toDto(ExerciseItem item) {
        String gifUrl = null;
        if (item.getGifUrl() != null && !item.getGifUrl().isBlank()) {
            // DB에 "chest/CHEST_PRESS.gif" 형태로 저장된 파일명에 환경별 prefix 붙이기
            // 경로가 포함된 경우 파일명만 추출 (기존 데이터 대비)
            String fileName = item.getGifUrl().contains("/")
                    ? item.getGifUrl()  // 이미 "폴더/파일.gif" 형태면 그대로
                    : item.getGifUrl(); // 파일명만 있어도 그대로
            gifUrl = gifBaseUrl + fileName;
        }

        return ExerciseItemDto.builder()
                .id(item.getId())
                .categoryId(item.getCategoryId())
                .nameKo(item.getNameKo())
                .nameEn(item.getNameEn())
                .description(item.getDescription())
                .equipmentType(item.getEquipmentType())
                .gifUrl(gifUrl)
                .youtubeUrl(item.getYoutubeUrl())
                .mediaUrl(item.getMediaUrl())
                .build();
    }
}

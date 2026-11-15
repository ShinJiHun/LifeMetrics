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

    static boolean isGif(byte[] b) {
        return b != null && b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F'
                && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
    }

    private static ResponseEntity<Map<String, Object>> badGif(String message) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
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

    static final long MAX_GIF_BYTES = 15L * 1024 * 1024;

    // ── GIF 업로드(관리자: 추가·교체) ─────────────────────────────
    /**
     * 운동의 GIF 를 올리거나 바꾼다. 이미 경로(gif_url)가 있으면 같은 경로에 덮어쓰고, 없으면 uploads/{id}.gif 로 저장해
     * DB 경로를 채운다. 덮어쓰기 전 기존 파일은 .backup/ 아래에 시각을 붙여 복사해 둔다.
     * 돌려주는 gifUrl 에는 ?v= 를 붙여 브라우저 캐시 때문에 옛 그림이 보이지 않게 한다.
     */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> uploadGif(Long itemId, MultipartFile file) {
        try {
            ExerciseItem item = exerciseItemRepository.findById(itemId).orElse(null);
            if (item == null) return ResponseEntity.status(404).body(Map.of("success", false, "message", "운동 항목이 없습니다."));
            if (file == null || file.isEmpty()) return badGif("파일이 비어 있습니다.");
            if (file.getSize() > MAX_GIF_BYTES) return badGif("GIF 는 15MB 이하만 올릴 수 있습니다.");
            byte[] bytes = file.getBytes();
            if (!isGif(bytes)) return badGif("GIF 파일만 올릴 수 있습니다.");

            String relative = item.getGifUrl() != null && !item.getGifUrl().isBlank()
                    ? item.getGifUrl() : "uploads/" + itemId + ".gif";
            Path base = Paths.get(gifBasePath).toAbsolutePath().normalize();
            Path target = base.resolve(relative).normalize();
            if (!target.startsWith(base)) return badGif("잘못된 파일 경로입니다.");
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                Path backup = base.resolve(".backup").resolve(relative.replace('/', '_') + "." + System.currentTimeMillis());
                Files.createDirectories(backup.getParent());
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(target, bytes);
            if (item.getGifUrl() == null || item.getGifUrl().isBlank()) {
                item.setGifUrl(relative);
                exerciseItemRepository.save(item);
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "gifUrl", gifBaseUrl + relative + "?v=" + System.currentTimeMillis(),
                    "message", "GIF 를 저장했습니다."
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

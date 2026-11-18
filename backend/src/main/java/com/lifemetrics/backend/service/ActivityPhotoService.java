package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.ActivityPhotoDto;
import com.lifemetrics.backend.entity.ActivityImage;
import com.lifemetrics.backend.repository.ActivityImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 활동 사진(블로그 후기 사진) 목록과 파일 서빙.
 * 파일은 블로그 보관 폴더(NAS persona/blog) 아래에 있고, 크기별로
 * thumb = thumb_path(긴 변 360px), web = image_path 의 images/ → web/*.jpg(긴 변 1600px), original = image_path.
 */
@Service
@RequiredArgsConstructor
public class ActivityPhotoService {

    private final ActivityImageRepository repository;

    @Value("${persona.blog.archive-path:/mnt/200gb/NAS/persona/blog}")
    private String archivePath;

    public List<ActivityPhotoDto> list(Long activityId) {
        return repository.findByActivityCoreIdOrderBySortOrderAscIdAsc(activityId).stream()
                .map(this::toDto)
                .toList();
    }

    public ResponseEntity<Resource> file(Long photoId, String size) {
        ActivityImage img = repository.findById(photoId).orElse(null);
        if (img == null || img.getImagePath() == null) return ResponseEntity.notFound().build();

        String rel = switch (size == null ? "web" : size) {
            case "thumb" -> img.getThumbPath() != null ? img.getThumbPath() : img.getImagePath();
            case "original" -> img.getImagePath();
            default -> webPath(img.getImagePath());
        };
        Path base = Paths.get(archivePath).toAbsolutePath().normalize();
        Path file = base.resolve(rel).normalize();
        if (!file.startsWith(base)) return ResponseEntity.badRequest().build();
        if (!Files.isRegularFile(file)) {
            // web 크기가 아직 없으면 원본으로
            file = base.resolve(img.getImagePath()).normalize();
            if (!file.startsWith(base) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        }
        MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(new FileSystemResource(file));
    }

    /** "athlete/029_x/images/02.gif" → "athlete/029_x/web/02.jpg" */
    static String webPath(String imagePath) {
        int slash = imagePath.lastIndexOf("/images/");
        if (slash < 0) return imagePath;
        String name = imagePath.substring(slash + "/images/".length());
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        return imagePath.substring(0, slash) + "/web/" + stem + ".jpg";
    }

    private ActivityPhotoDto toDto(ActivityImage i) {
        String base = "/api/activity/photos/" + i.getId() + "/file?size=";
        return new ActivityPhotoDto(
                i.getId(), i.getSortOrder(),
                base + "thumb", base + "web", base + "original",
                i.getTakenAt(), i.getLat(), i.getLon(),
                i.getDistanceM() == null ? null : Math.round(i.getDistanceM() / 100.0) / 10.0,
                i.getLocationSource(), i.getCaption(), i.getWidth(), i.getHeight(),
                i.getBlogPostNo(), i.getBlogPostTitle(), i.getBlogPostUrl());
    }
}

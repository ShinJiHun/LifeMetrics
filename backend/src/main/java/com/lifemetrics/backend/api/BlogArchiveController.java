package com.lifemetrics.backend.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 블로그 보관 폴더(NAS persona/blog, data_pipeline/persona/blog_archive.py)의 이미지 서빙.
 * 휴먼 블로그로 옮긴 글(blog_to_journal.py)의 본문 이미지가 이 주소를 쓴다:
 * GET /api/blog-archive/file?path=athlete/029_.../web/02.jpg
 * 원래 공개 블로그에 있던 사진이라 GET 공개. 이미지 확장자만, 보관 폴더 밖 경로는 거절.
 */
@RestController
@RequestMapping("/api/blog-archive")
public class BlogArchiveController {

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${persona.blog.archive-path:/mnt/200gb/NAS/persona/blog}")
    private String archivePath;

    @GetMapping("/file")
    public ResponseEntity<Resource> file(@RequestParam String path) {
        Path base = Paths.get(archivePath).toAbsolutePath().normalize();
        Path file = base.resolve(path).normalize();
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!file.startsWith(base) || !IMAGE_EXT.contains(ext)) return ResponseEntity.badRequest().build();
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(new FileSystemResource(file));
    }
}

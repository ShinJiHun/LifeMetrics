package com.lifemetrics.backend.persona.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 블로그 글(journal_post) 본문 이미지 — 글쓰기 에디터에서 올린 파일을 NAS 에 저장하고 서빙한다.
 * <p>
 * 저장: {@code blog.image.path}/{yyyy}/{MM}/{uuid}.{ext}. 올리기는 AdminWriteFilter 가 관리자만 허용,
 * 보기는 공개 글에 들어가므로 누구나(파일명은 추측 불가한 uuid).
 */
@RestController
@RequestMapping("/api/content/images")
public class BlogImageController {

    private static final Map<String, String> TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif", "webp", "image/webp");
    private static final Pattern NAME = Pattern.compile("^[0-9a-f]{32}\\.(jpg|jpeg|png|gif|webp)$");
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Value("${blog.image.path:/tmp/blog-images/}")
    private String basePath;

    @PostMapping
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw bad("빈 파일입니다.");
        if (file.getSize() > MAX_BYTES) throw bad("이미지는 20MB 까지 올릴 수 있습니다.");
        byte[] data = file.getBytes();
        String ext = sniff(data);
        if (ext == null) throw bad("jpg · png · gif · webp 이미지만 올릴 수 있습니다.");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String yyyy = String.valueOf(today.getYear());
        String mm = String.format("%02d", today.getMonthValue());
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = root().resolve(yyyy).resolve(mm);
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), data);
        return Map.of("url", "/api/content/images/" + yyyy + "/" + mm + "/" + name);
    }

    @GetMapping("/{yyyy:\\d{4}}/{mm:\\d{2}}/{name:.+}")
    public ResponseEntity<Resource> view(@PathVariable String yyyy, @PathVariable String mm, @PathVariable String name) {
        if (!NAME.matcher(name).matches()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path root = root();
        Path path = root.resolve(yyyy).resolve(mm).resolve(name).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String ext = name.substring(name.lastIndexOf('.') + 1);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(TYPES.get(ext)))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(new FileSystemResource(path));
    }

    /** 파일 앞부분(매직 넘버)으로 형식 확인 — 확장자·Content-Type 은 믿지 않는다. */
    static String sniff(byte[] d) {
        if (d.length < 12) return null;
        if ((d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xD8) return "jpg";
        if ((d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "png";
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F') return "gif";
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "webp";
        return null;
    }

    private Path root() {
        return Paths.get(basePath).toAbsolutePath().normalize();
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }
}

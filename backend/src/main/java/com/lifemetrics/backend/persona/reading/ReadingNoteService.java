package com.lifemetrics.backend.persona.reading;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 독서 노트 기록 파일(독서 앱에서 내보낸 CSV) 보관.
 * <p>
 * 파일: {@code reading.note.path}/uploads/{yyyyMMdd-HHmmss}__{원본이름}.csv
 * <p>
 * 글(독서 노트·후기)은 앱에서 자동으로 만들지 않는다. 사용자가 요청하면 Claude Code 가
 * 이 폴더의 파일을 읽어 휴먼 블로그 '독서' 메뉴에 글로 넣는다.
 */
@Slf4j
@Service
public class ReadingNoteService {

    static final Set<String> ALLOWED_EXT = Set.of("csv");
    private static final Pattern STORED_NAME =
            Pattern.compile("^(\\d{8}-\\d{6})__([^/\\\\]+)\\.([a-z0-9]{1,5})$");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Value("${reading.note.path:/tmp/reading/}")
    private String basePath;

    public record NoteFile(String filename, String originalName, long size, Instant uploadedAt, Integer rows) {}

    public List<NoteFile> list() throws IOException {
        Path dir = uploadsDir();
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(NoteFile::filename).reversed())
                    .toList();
        }
    }

    public List<NoteFile> upload(List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) throw bad("파일을 골라 주세요.");
        Path dir = uploadsDir();
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now(KST).format(STAMP);
        List<NoteFile> saved = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f.isEmpty()) continue;
            String ext = extension(f.getOriginalFilename());
            if (!ALLOWED_EXT.contains(ext)) {
                throw bad("CSV 파일만 올릴 수 있습니다: " + f.getOriginalFilename());
            }
            Path target = uniqueTarget(dir, stamp + "__" + safeBaseName(f.getOriginalFilename(), ext), ext);
            Files.write(target, f.getBytes());
            log.info("[reading-note] uploaded {} ({} bytes)", target.getFileName(), f.getSize());
            saved.add(toDto(target));
        }
        if (saved.isEmpty()) throw bad("빈 파일입니다.");
        return saved;
    }

    public void delete(String filename) throws IOException {
        Files.delete(resolveExisting(filename));
    }

    public Path resolveExisting(String filename) {
        if (filename == null || !STORED_NAME.matcher(filename).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일이 없습니다.");
        }
        Path dir = uploadsDir();
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일이 없습니다.");
        }
        return path;
    }

    // ── helpers ─────────────────────────────

    private Path uploadsDir() {
        return Paths.get(basePath).toAbsolutePath().normalize().resolve("uploads");
    }

    private NoteFile toDto(Path p) {
        String name = p.getFileName().toString();
        Matcher m = STORED_NAME.matcher(name);
        if (!m.matches()) return null;
        try {
            return new NoteFile(name, m.group(2) + "." + m.group(3), Files.size(p),
                    Files.getLastModifiedTime(p).toInstant(), countRows(p));
        } catch (IOException e) {
            return null;
        }
    }

    /** 대략적인 행 수(머리글 제외, 따옴표 안 줄바꿈 고려). 못 세면 null. */
    static Integer countRows(Path p) {
        try {
            byte[] data = Files.readAllBytes(p);
            int rows = 0;
            boolean inQuote = false, lineHasContent = false;
            for (byte b : data) {
                if (b == '"') inQuote = !inQuote;
                if (b == '\n' && !inQuote) {
                    if (lineHasContent) rows++;
                    lineHasContent = false;
                } else if (b != '\r') {
                    lineHasContent = true;
                }
            }
            if (lineHasContent) rows++;
            return Math.max(0, rows - 1);
        } catch (IOException e) {
            return null;
        }
    }

    private static Path uniqueTarget(Path dir, String base, String ext) {
        Path target = dir.resolve(base + "." + ext);
        for (int i = 2; Files.exists(target); i++) target = dir.resolve(base + "_" + i + "." + ext);
        return target;
    }

    static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 원본 파일명에서 확장자를 떼고 경로·특수문자를 정리한다. 한글은 유지. */
    static String safeBaseName(String original, String ext) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (!ext.isEmpty() && name.toLowerCase(Locale.ROOT).endsWith("." + ext)) {
            name = name.substring(0, name.length() - ext.length() - 1);
        }
        name = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").replaceAll("_{2,}", "_");
        name = name.replaceAll("^[\\s._]+|[\\s._]+$", "");
        if (name.length() > 80) name = name.substring(0, 80);
        return name.isBlank() ? "독서기록" : name;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}

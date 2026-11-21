package com.lifemetrics.backend.persona.diary;

import com.lifemetrics.backend.service.DailyAnalysisService;
import com.lifemetrics.backend.service.SttService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * 직접 기록한 강연의 자료 파일. NAS {@code lecture.files-dir}/{강연 id}/ 에 보관하고(운영 entries/, dev entries-dev/),
 * 요약 근거로 쓸 글을 lecture_file.extracted_text 에 둔다 — .md/.txt 는 내용 그대로, PDF 는 글 추출, 녹음은 STT 받아쓰기.
 * <p>
 * 녹음 받아쓰기는 수십 분 걸릴 수 있어 백그라운드 스레드 하나에서 차례로 한다. STT 서버가 꺼져 있으면 '받아쓰기 전'으로 둔다.
 * NAS 강연 폴더({@code lecture.nas-dir}, 예전 자료 raw/·transcripts/)의 파일은 원본을 옮기지 않고 복사해 가져온다.
 * 컨테이너(root)가 만든 파일·폴더는 일기 inbox 처럼 DIARY_INBOX_OWNER(uid:gid) 소유로 돌린다.
 */
@Slf4j
@Service
public class LectureFileService {

    static final Set<String> TEXT_EXT = Set.of("md", "txt");
    static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "heic");
    static final Set<String> AUDIO_EXT = Set.of("m4a", "mp3");
    private static final int MAX_TEXT_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_NAS_LIST = 1000;
    private static final Duration STT_TIMEOUT = Duration.ofMinutes(90);

    public record NasFile(String path, long sizeBytes, String modifiedAt) {}
    public record Download(Path path, String fileName, String contentType) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final SttService stt;
    private final Path nasDir;
    private final Path filesDir;
    private final int[] owner;
    private final ExecutorService sttWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lecture-stt");
        t.setDaemon(true);
        return t;
    });

    public LectureFileService(@Qualifier("journalDataSource") DataSource dataSource, SttService stt,
                              @Value("${lecture.nas-dir:${LECTURE_NAS_DIR:/mnt/200gb/NAS/persona/lectures}}") String nasDir,
                              @Value("${lecture.files-dir:${LECTURE_FILES_DIR:}}") String filesDir,
                              @Value("${diary.inbox-owner:${DIARY_INBOX_OWNER:}}") String owner) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.stt = stt;
        this.nasDir = Path.of(nasDir).toAbsolutePath().normalize();
        this.filesDir = (filesDir == null || filesDir.isBlank() ? this.nasDir.resolve("entries") : Path.of(filesDir))
                .toAbsolutePath().normalize();
        this.owner = DiaryService.parseOwner(owner);
    }

    /** 서버가 다시 뜨면 진행 중이던 받아쓰기는 끊긴 것이다 → 실패로 돌려 다시 누를 수 있게. 표가 아직 없어도 기동은 막지 않는다. */
    @PostConstruct
    void recoverInterrupted() {
        try {
            int n = jdbc.update("""
                    UPDATE lecture_file SET text_status = 'failed', text_note = '서버가 다시 시작돼 받아쓰기가 끊겼습니다. 다시 눌러 주세요.'
                    WHERE text_status = 'transcribing'
                    """, new MapSqlParameterSource());
            if (n > 0) log.info("끊긴 강연 녹음 받아쓰기 {}건을 실패로 표시", n);
        } catch (Exception e) {
            log.debug("lecture_file 정리 건너뜀: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        sttWorker.shutdownNow();
    }

    // ── 올리기·가져오기 ──────────────────────────────

    public List<LectureService.LectureFile> upload(long entryId, List<MultipartFile> files) {
        requireEntry(entryId);
        if (files == null || files.isEmpty()) throw bad("파일을 골라 주세요.");
        List<Long> ids = new ArrayList<>();
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename() == null ? "file" : f.getOriginalFilename();
            try (InputStream in = f.getInputStream()) {
                ids.add(store(entryId, name, in));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 저장하지 못했습니다: " + name);
            }
        }
        return list(ids);
    }

    /** NAS 강연 폴더의 파일을 복사해 가져온다(원본은 그대로). paths 는 {@link #listNas()} 가 준 상대 경로. */
    public List<LectureService.LectureFile> importNas(long entryId, List<String> paths) {
        requireEntry(entryId);
        if (paths == null || paths.isEmpty()) throw bad("가져올 파일을 골라 주세요.");
        List<Long> ids = new ArrayList<>();
        for (String rel : paths) {
            Path src = nasPath(rel);
            try (InputStream in = Files.newInputStream(src)) {
                ids.add(store(entryId, src.getFileName().toString(), in));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 복사하지 못했습니다: " + rel);
            }
        }
        return list(ids);
    }

    /** 가져올 수 있는 NAS 강연 폴더 파일(올린 자료 보관 폴더 entries*·deleted 는 뺀다). 최근 수정 순. */
    public List<NasFile> listNas() {
        if (!Files.isDirectory(nasDir)) return List.of();
        List<NasFile> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(nasDir, 4)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                if (!importable(p)) continue;
                out.add(new NasFile(nasDir.relativize(p).toString(), Files.size(p),
                        LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), DailyAnalysisService.KST).withNano(0).toString()));
            }
        } catch (IOException e) {
            log.warn("강연 NAS 폴더 읽기 실패 {}: {}", nasDir, e.getMessage());
        }
        out.sort(Comparator.comparing(NasFile::modifiedAt).reversed());
        return out.size() > MAX_NAS_LIST ? out.subList(0, MAX_NAS_LIST) : out;
    }

    private boolean importable(Path p) {
        Path rel = nasDir.relativize(p);
        String top = rel.getName(0).toString();
        if (rel.getNameCount() > 1 && (top.startsWith("entries") || p.startsWith(filesDir))) return false;
        for (Path part : rel) if (part.toString().startsWith(".") || part.toString().equals("deleted")) return false;
        return kindOf(p.getFileName().toString()) != null;
    }

    private Path nasPath(String rel) {
        if (rel == null || rel.isBlank()) throw bad("경로가 비어 있습니다.");
        Path p = nasDir.resolve(rel).normalize();
        if (!p.startsWith(nasDir) || !Files.isRegularFile(p) || !importable(p)) throw bad("가져올 수 없는 파일입니다: " + rel);
        return p;
    }

    /** NAS 에 복사하고 글을 뽑아 lecture_file 에 넣는다. 녹음이면 STT 가 떠 있을 때 받아쓰기를 건다. */
    private long store(long entryId, String originalName, InputStream in) throws IOException {
        String name = safeName(originalName);
        String kind = kindOf(name);
        if (kind == null) throw bad("올릴 수 없는 파일 형식입니다: " + originalName + " (.md .txt .pdf .png .jpg .heic .m4a .mp3)");
        Path dir = createDirs(filesDir.resolve(Long.toString(entryId)));
        Path target = dir.resolve(name);
        if (Files.exists(target)) target = dir.resolve(System.currentTimeMillis() + "-" + name);
        long size = Files.copy(in, target);
        chown(target);

        String text = null, status, note = null;
        switch (kind) {
            case "text" -> {
                if (size > MAX_TEXT_FILE_BYTES) {
                    status = "none";
                    note = "글 파일이 너무 큽니다(5MB 초과).";
                } else {
                    text = stripBom(Files.readString(target, StandardCharsets.UTF_8)).strip();
                    status = text.isEmpty() ? "none" : "ready";
                }
            }
            case "pdf" -> {
                text = pdfText(target);
                status = text != null && text.length() >= 30 ? "ready" : "none";
                if (!"ready".equals(status)) {
                    note = text == null ? "PDF 를 읽지 못했습니다." : "글자가 없는 PDF 입니다(스캔 이미지). 요약에 쓰지 않습니다.";
                    text = null;
                }
            }
            case "audio" -> status = "pending";
            default -> status = "none";
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        LocalDateTime now = now();
        jdbc.update("""
                INSERT INTO lecture_file (entry_id, kind, file_name, stored_name, size_bytes, extracted_text, text_status, text_note,
                                          created_at, text_updated_at)
                VALUES (:e, :k, :f, :s, :size, :text, :st, :note, :t, :tu)
                """, new MapSqlParameterSource("e", entryId).addValue("k", kind).addValue("f", cut(originalName.strip(), 255))
                .addValue("s", target.getFileName().toString()).addValue("size", size).addValue("text", text)
                .addValue("st", status).addValue("note", note).addValue("t", now).addValue("tu", text == null ? null : now),
                key, new String[]{"id"});
        long id = key.getKey().longValue();
        if ("audio".equals(kind) && stt.isAvailable()) startTranscribe(id);
        return id;
    }

    // ── 받아쓰기 ─────────────────────────────────────

    /** 녹음 받아쓰기를 (다시) 건다. STT 서버가 꺼져 있으면 받아쓰기 파일을 올리라고 안내한다. */
    public LectureService.LectureFile transcribe(long fileId) {
        Map<String, Object> row = row(fileId);
        if (!"audio".equals(row.get("kind"))) throw bad("녹음 파일만 받아쓸 수 있습니다.");
        if ("transcribing".equals(row.get("text_status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 받아쓰는 중입니다.");
        if (!stt.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "STT 서버가 꺼져 있어 녹음을 받아쓸 수 없습니다. 받아쓰기 파일(.txt/.md)을 올려 주세요.");
        }
        startTranscribe(fileId);
        return list(List.of(fileId)).get(0);
    }

    private void startTranscribe(long fileId) {
        jdbc.update("UPDATE lecture_file SET text_status = 'transcribing', text_note = NULL, text_updated_at = :t WHERE id = :id",
                new MapSqlParameterSource("id", fileId).addValue("t", now()));
        sttWorker.submit(() -> runTranscribe(fileId));
    }

    private void runTranscribe(long fileId) {
        try {
            Map<String, Object> row = row(fileId);
            Path p = storedPath(row);
            String ext = ext(p.getFileName().toString());
            String text = stt.transcribeLong(Files.readAllBytes(p), "mp3".equals(ext) ? "audio/mpeg" : "audio/mp4", STT_TIMEOUT);
            jdbc.update("""
                    UPDATE lecture_file SET extracted_text = :text, text_status = :st, text_note = :note, text_updated_at = :t WHERE id = :id
                    """, new MapSqlParameterSource("id", fileId).addValue("text", text.strip())
                    .addValue("st", text.isBlank() ? "failed" : "ready")
                    .addValue("note", text.isBlank() ? "받아쓴 글이 비어 있습니다." : null).addValue("t", now()));
            log.info("강연 녹음 받아쓰기 완료 (파일 {}, {}자)", fileId, text.length());
        } catch (Throwable e) {
            log.warn("강연 녹음 받아쓰기 실패 (파일 {}): {}", fileId, e.getMessage());
            try {
                jdbc.update("UPDATE lecture_file SET text_status = 'failed', text_note = :note, text_updated_at = :t WHERE id = :id",
                        new MapSqlParameterSource("id", fileId).addValue("note", cut(String.valueOf(e.getMessage()), 255))
                                .addValue("t", now()));
            } catch (Exception ignored) {
                // 파일이 그새 지워졌으면 할 일 없음
            }
        }
    }

    // ── 보기·지우기 ──────────────────────────────────

    public Download download(long fileId) {
        Map<String, Object> row = row(fileId);
        Path p = storedPath(row);
        if (!Files.isRegularFile(p)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NAS 에서 파일을 찾을 수 없습니다.");
        return new Download(p, (String) row.get("file_name"), contentType(p.getFileName().toString()));
    }

    /** 목록에서 지운다. NAS 파일은 지우지 않고 그 강연 폴더의 deleted/ 로 옮겨 둔다. */
    public void delete(long fileId) {
        Map<String, Object> row = row(fileId);
        if ("transcribing".equals(row.get("text_status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "받아쓰는 중에는 지울 수 없습니다.");
        jdbc.update("DELETE FROM lecture_file WHERE id = :id", new MapSqlParameterSource("id", fileId));
        Path p = storedPath(row);
        if (!Files.isRegularFile(p)) return;
        try {
            Path trash = createDirs(p.getParent().resolve("deleted"));
            Files.move(p, trash.resolve(System.currentTimeMillis() + "-" + p.getFileName()));
        } catch (IOException e) {
            log.warn("강연 자료 옮기기 실패 {}: {}", p, e.getMessage());
        }
    }

    // ── 내부 ────────────────────────────────────────

    /** 파일 이름으로 종류를 정한다. 받을 수 없는 형식이면 null. */
    static String kindOf(String name) {
        String ext = ext(name);
        if (TEXT_EXT.contains(ext)) return "text";
        if ("pdf".equals(ext)) return "pdf";
        if (IMAGE_EXT.contains(ext)) return "image";
        if (AUDIO_EXT.contains(ext)) return "audio";
        return null;
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 경로 문자·제어 문자를 없앤 저장 이름. */
    static String safeName(String original) {
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}:*?\"<>|]", "_").strip();
        if (base.startsWith(".")) base = "_" + base.substring(1);
        if (base.isBlank()) base = "file";
        if (base.length() > 180) {
            String e = ext(base);
            base = base.substring(0, 170) + (e.isEmpty() ? "" : "." + e);
        }
        return base;
    }

    static String contentType(String name) {
        return switch (ext(name)) {
            case "md" -> "text/markdown; charset=UTF-8";
            case "txt" -> "text/plain; charset=UTF-8";
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "heic" -> "image/heic";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            default -> "application/octet-stream";
        };
    }

    private static String pdfText(Path p) {
        try (PDDocument doc = Loader.loadPDF(p.toFile())) {
            return new PDFTextStripper().getText(doc).replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
        } catch (Exception e) {
            log.warn("강연 자료 PDF 읽기 실패 {}: {}", p, e.getMessage());
            return null;
        }
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    private Path storedPath(Map<String, Object> row) {
        Path dir = filesDir.resolve(String.valueOf(row.get("entry_id")));
        Path p = dir.resolve((String) row.get("stored_name")).normalize();
        if (!p.startsWith(dir)) throw bad("잘못된 파일 경로입니다.");
        return p;
    }

    private Map<String, Object> row(long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, entry_id, kind, file_name, stored_name, text_status FROM lecture_file WHERE id = :id",
                new MapSqlParameterSource("id", fileId));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자료 파일을 찾을 수 없습니다.");
        return rows.get(0);
    }

    private List<LectureService.LectureFile> list(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT id, entry_id, kind, file_name, size_bytes, text_status, text_note, CHAR_LENGTH(extracted_text) AS chars, created_at
                FROM lecture_file WHERE id IN (:ids) ORDER BY id
                """, new MapSqlParameterSource("ids", ids), (rs, i) -> LectureService.mapFile(rs));
    }

    private void requireEntry(long entryId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lecture_entry WHERE id = :id", new MapSqlParameterSource("id", entryId), Integer.class);
        if (n == null || n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "강연을 찾을 수 없습니다.");
    }

    /** dir 을 만들고, 이번에 새로 생긴 폴더(filesDir 포함)는 소유자를 바꾼다. */
    private Path createDirs(Path dir) throws IOException {
        List<Path> created = new ArrayList<>();
        for (Path d = dir; d != null && d.startsWith(filesDir) && !Files.exists(d); d = d.getParent()) created.add(d);
        Files.createDirectories(dir);
        for (Path d : created) chown(d);
        return dir;
    }

    private void chown(Path p) {
        if (owner == null) return;
        try {
            Files.setAttribute(p, "unix:uid", owner[0], LinkOption.NOFOLLOW_LINKS);
            Files.setAttribute(p, "unix:gid", owner[1], LinkOption.NOFOLLOW_LINKS);
        } catch (Exception e) {
            log.debug("강연 자료 소유자 변경 실패 {}: {}", p, e.getMessage());
        }
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(DailyAnalysisService.KST).withNano(0);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}

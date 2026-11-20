package com.lifemetrics.backend.persona.reading;

import com.lifemetrics.backend.persona.reading.ReadingNoteService.NoteFile;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 인간 페르소나 — 독서 노트 기록 파일(CSV) 업로드·보관 (관리자 전용).
 * <p>
 * NAS {@code reading.note.path}/uploads 에 저장만 한다. 글은 사용자 요청 시 Claude Code 가
 * 파일을 읽어 휴먼 블로그 '독서' 메뉴에 넣는다.
 * <p>
 * 쓰기는 AdminWriteFilter 가 막고, 읽기는 개인 기록이라 여기서 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/human/reading-notes")
@RequiredArgsConstructor
public class ReadingNoteController {

    private final AdminWriteFilter adminWriteFilter;
    private final ReadingNoteService service;

    @GetMapping
    public List<NoteFile> list(HttpServletRequest request) throws IOException {
        requireAdmin(request);
        return service.list();
    }

    @PostMapping
    public List<NoteFile> upload(@RequestParam("files") List<MultipartFile> files) throws IOException {
        return service.upload(files);
    }

    /** 원본 CSV 내려받기. */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> download(HttpServletRequest request, @PathVariable String filename) {
        requireAdmin(request);
        Path path = service.resolveExisting(filename);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/{filename:.+}")
    public void delete(@PathVariable String filename) throws IOException {
        service.delete(filename);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}

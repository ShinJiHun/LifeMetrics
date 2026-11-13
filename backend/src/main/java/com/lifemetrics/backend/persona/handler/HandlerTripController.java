package com.lifemetrics.backend.persona.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 인간 페르소나 — 핸들러 핸들 1건씩 올리기 (관리자 전용).
 * 흐름: 캡처 → POST /extract (판독 + 경로 미리보기) → 필요하면 고쳐서 POST /resolve → POST / 저장.
 * 쓰기는 AdminWriteFilter 가 막고, 읽기는 위치·수입이 있어 여기서 관리자만 허용한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/human/handler-trips")
@RequiredArgsConstructor
public class HandlerTripController {

    private final HandlerTripService service;
    private final AdminWriteFilter adminWriteFilter;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<HandlerTripService.Trip> list(HttpServletRequest request,
                                              @RequestParam(defaultValue = "1") Long userId) {
        requireAdmin(request);
        return service.list(userId);
    }

    @PostMapping("/extract")
    public HandlerTripService.Preview extract(@RequestParam("file") MultipartFile file,
                                              @RequestParam("year") int year) throws IOException {
        if (file.isEmpty()) throw bad("빈 파일입니다.");
        if (year < 2000 || year > java.time.LocalDate.now().getYear() + 1) throw bad("연도를 확인해 주세요.");
        try {
            return service.extract(file.getOriginalFilename(), file.getContentType(), file.getBytes(), year);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw bad(e.getMessage());
        }
    }

    /** 주소·이름을 고친 뒤 위치·경로만 다시 찾는다. */
    @PostMapping("/resolve")
    public HandlerTripService.Preview resolve(@RequestBody HandlerTripService.Draft draft) {
        return service.resolve(draft, null);
    }

    /** 저장. data = Draft JSON, file = 캡처(선택), raw = 판독 원문(선택). 위치·경로는 저장 시점에 다시 찾는다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HandlerTripService.Trip save(@RequestParam("data") String data,
                                        @RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "raw", required = false) String raw,
                                        @RequestParam(defaultValue = "1") Long userId) throws IOException {
        HandlerTripService.Draft draft;
        try {
            draft = objectMapper.readValue(data, HandlerTripService.Draft.class);
        } catch (Exception e) {
            throw bad("입력값을 읽지 못했습니다.");
        }
        try {
            return service.save(draft, userId,
                    file != null ? file.getOriginalFilename() : null,
                    file != null ? file.getContentType() : null,
                    file != null && !file.isEmpty() ? file.getBytes() : null, raw);
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(HttpServletRequest request, @PathVariable long id) {
        requireAdmin(request);
        var path = service.imagePath(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(path.getFileName().toString()).orElse(MediaType.IMAGE_JPEG))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) throws IOException {
        if (!service.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 기록입니다.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}

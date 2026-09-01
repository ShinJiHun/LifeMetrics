package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.HealthCheckupDto;
import com.lifemetrics.backend.security.AdminWriteFilter;
import com.lifemetrics.backend.service.HealthCheckupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 건강검진(국민건강보험공단 결과통보서) 기록 — 관리자 전용.
 * <p>
 * 쓰기(POST/PUT/DELETE)는 AdminWriteFilter 가 이미 관리자로 제한한다.
 * 읽기(GET)는 기본적으로 누구나 가능하므로 여기서 명시적으로 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/body/health-checkups")
@RequiredArgsConstructor
public class HealthCheckupController {

    private final HealthCheckupService service;
    private final AdminWriteFilter adminWriteFilter;

    private void requireAdmin(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            throw new AccessDeniedException();
        }
    }

    @GetMapping
    public List<HealthCheckupDto> list(HttpServletRequest request,
                                       @RequestParam(defaultValue = "1") Long userId) {
        requireAdmin(request);
        return service.list(userId);
    }

    @GetMapping("/{id}")
    public HealthCheckupDto get(HttpServletRequest request, @PathVariable Long id) {
        requireAdmin(request);
        return service.get(id);
    }

    /** PDF 업로드 → AI 추출 결과 반환(저장하지 않음). 화면에서 검토 후 POST 로 저장한다. */
    @PostMapping("/extract")
    public ResponseEntity<?> extract(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(service.extractFromPdf(file));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(400).body(Map.of("message", "PDF를 읽지 못했습니다."));
        }
    }

    @PostMapping
    public HealthCheckupDto create(@RequestParam(defaultValue = "1") Long userId,
                                   @RequestBody HealthCheckupDto req) {
        return service.create(userId, req);
    }

    @PutMapping("/{id}")
    public HealthCheckupDto update(@PathVariable Long id, @RequestBody HealthCheckupDto req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    static class AccessDeniedException extends RuntimeException {
        AccessDeniedException() {
            super("관리자만 접근할 수 있습니다.");
        }
    }
}

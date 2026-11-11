package com.lifemetrics.backend.health.controller;

import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.service.HealthTreatmentService;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 치료·부상 기록 — 관리자 전용.
 * <p>
 * 쓰기(POST/PUT/DELETE)는 AdminWriteFilter 가 이미 관리자로 제한한다.
 * 읽기(GET)는 기본적으로 누구나 가능하므로 여기서 명시적으로 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/body/treatments")
@RequiredArgsConstructor
public class HealthTreatmentController {

    private final HealthTreatmentService service;
    private final AdminWriteFilter adminWriteFilter;

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request, @RequestParam(defaultValue = "1") Long userId) {
        if (!adminWriteFilter.isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        return ResponseEntity.ok(service.list(userId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestParam(defaultValue = "1") Long userId, @RequestBody HealthTreatment req) {
        try {
            return ResponseEntity.ok(service.create(userId, req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody HealthTreatment req) {
        try {
            return service.update(id, req)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}

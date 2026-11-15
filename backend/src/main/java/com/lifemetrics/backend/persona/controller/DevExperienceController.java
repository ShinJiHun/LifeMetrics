package com.lifemetrics.backend.persona.controller;

import com.lifemetrics.backend.persona.entity.DevExperience;
import com.lifemetrics.backend.persona.repository.DevExperienceRepository;
import com.lifemetrics.backend.persona.service.DevExperienceService;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 개발자 경험 카드. 조회는 누구나(공개 카드만), 관리자는 비공개까지.
 * 쓰기(POST/PUT/DELETE, 초안 생성)는 AdminWriteFilter 가 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/persona/experiences")
@RequiredArgsConstructor
public class DevExperienceController {

    private final DevExperienceService service;
    private final DevExperienceRepository repo;
    private final AdminWriteFilter adminWriteFilter;

    @GetMapping
    public Object list(HttpServletRequest request) {
        return service.list(adminWriteFilter.isAdmin(request));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            return save(DevExperienceService.fromMap(body, new DevExperience()));
        } catch (DateTimeParseException e) {
            return bad("날짜 형식은 YYYY-MM-DD 입니다.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return repo.findById(id)
                    .<ResponseEntity<?>>map(e -> save(DevExperienceService.fromMap(body, e)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (DateTimeParseException e) {
            return bad("날짜 형식은 YYYY-MM-DD 입니다.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** 커밋 기록·메모 → 카드 초안 (저장 안 함). body: {"project": "...", "notes": "..."} */
    @PostMapping("/draft")
    public ResponseEntity<?> draft(@RequestBody Map<String, String> body) {
        try {
            String project = body.getOrDefault("project", "").strip();
            if (project.isEmpty()) return bad("프로젝트명은 필수입니다.");
            return ResponseEntity.ok(service.draft(project, body.get("notes")));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<?> save(DevExperience e) {
        try {
            return ResponseEntity.ok(service.save(e));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return bad(ex.getMessage());
        }
    }

    private static ResponseEntity<?> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}

package com.lifemetrics.backend.health.controller;

import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.entity.HealthTreatmentVisit;
import com.lifemetrics.backend.health.service.HealthTreatmentService;
import com.lifemetrics.backend.health.service.TreatmentDocumentService;
import com.lifemetrics.backend.health.service.TreatmentVisitService;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
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
    private final TreatmentDocumentService documentService;
    private final TreatmentVisitService visitService;
    private final AdminWriteFilter adminWriteFilter;

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request, @RequestParam(defaultValue = "1") Long userId) {
        if (!adminWriteFilter.isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        return ResponseEntity.ok(service.list(userId));
    }

    /** 진단서·소견서(PDF·사진) → AI 추출 결과. 저장하지 않으며 화면에서 검토 후 POST 로 저장한다. */
    @PostMapping("/extract")
    public ResponseEntity<?> extract(@RequestParam(value = "files", required = false) List<MultipartFile> files,
                                     @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            return ResponseEntity.ok(documentService.extract(uploads(files, file)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "파일을 읽지 못했습니다."));
        }
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

    // ── 진료·처방 내역 (처방전·영수증·세부내역서) ──────────────────

    @GetMapping("/visits")
    public ResponseEntity<?> visits(HttpServletRequest request, @RequestParam(defaultValue = "1") Long userId) {
        if (!adminWriteFilter.isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        return ResponseEntity.ok(visitService.list(userId));
    }

    /** 처방전·약제비 영수증·진료비 세부내역서·처방조제내역(PDF·사진) → 날짜별 진료 미리보기. 저장하지 않는다. */
    @PostMapping("/{id}/visits/extract")
    public ResponseEntity<?> extractVisits(@PathVariable Long id,
                                           @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                           @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            return ResponseEntity.ok(visitService.extract(id, uploads(files, file)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "파일을 읽지 못했습니다."));
        }
    }

    @PostMapping("/{id}/visits")
    public ResponseEntity<?> saveVisits(@PathVariable Long id, @RequestBody List<HealthTreatmentVisit> visits) {
        try {
            return ResponseEntity.ok(visitService.save(id, visits));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/visits/{visitId}")
    public ResponseEntity<?> deleteVisit(@PathVariable Long visitId) {
        return visitService.delete(visitId) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** files(여러 장) 우선, 예전 클라이언트의 file(1장)도 받는다. */
    private static List<TreatmentDocumentService.Upload> uploads(List<MultipartFile> files, MultipartFile file) throws IOException {
        List<MultipartFile> all = new ArrayList<>();
        if (files != null) all.addAll(files);
        if (file != null) all.add(file);
        List<TreatmentDocumentService.Upload> out = new ArrayList<>();
        for (MultipartFile f : all) {
            if (f.isEmpty()) continue;
            out.add(new TreatmentDocumentService.Upload(f.getOriginalFilename(), f.getContentType(), f.getBytes()));
        }
        return out;
    }
}

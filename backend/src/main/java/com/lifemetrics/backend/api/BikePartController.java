package com.lifemetrics.backend.api;

import com.lifemetrics.backend.entity.BikePart;
import com.lifemetrics.backend.service.BikePartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 장비 변경 계획 — 자전거 소모품 교체 주기.
 * 쓰기(POST/PUT)는 AdminWriteFilter 가 관리자만 허용한다. 삭제 대신 교체/탈거(removedOn)로 이력을 남긴다.
 */
@RestController
@RequestMapping("/api/bike-parts")
@RequiredArgsConstructor
public class BikePartController {

    private final BikePartService service;

    @GetMapping("/plan")
    public List<BikePartService.BikePlan> plan(@RequestParam(defaultValue = "false") boolean includeRetired) {
        return service.plan(includeRetired);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BikePart request) {
        try {
            return ResponseEntity.ok(service.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody BikePart request) {
        try {
            return service.update(id, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 순정 소모품 일괄 등록 (구입일 기준). 이미 사용 중인 종류는 건너뛴다. */
    @PostMapping("/bike/{bikeId}/stock")
    public ResponseEntity<?> registerStock(@PathVariable Long bikeId) {
        try {
            return ResponseEntity.ok(service.registerStock(bikeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record ReplaceRequest(LocalDate date, String name, String note) {}

    @PostMapping("/{id}/replace")
    public ResponseEntity<?> replace(@PathVariable Long id, @RequestBody(required = false) ReplaceRequest request) {
        ReplaceRequest r = request != null ? request : new ReplaceRequest(null, null, null);
        try {
            return service.replace(id, r.date(), r.name(), r.note())
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

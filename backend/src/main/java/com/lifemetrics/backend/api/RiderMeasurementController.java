package com.lifemetrics.backend.api;

import com.lifemetrics.backend.entity.RiderMeasurement;
import com.lifemetrics.backend.repository.RiderMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 라이더 신체 치수(피팅용). 쓰기(POST/PUT/DELETE)는 AdminWriteFilter 가 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/rider-measurements")
@RequiredArgsConstructor
public class RiderMeasurementController {

    private static final Set<String> ARCH_TYPES = Set.of("LOW", "MEDIUM", "HIGH");

    private final RiderMeasurementRepository repository;

    /** 최신 측정부터 */
    @GetMapping
    public List<RiderMeasurement> list(@RequestParam(defaultValue = "1") Long userId) {
        return repository.findByUserIdOrderByMeasureDateDescIdDesc(userId);
    }

    @PostMapping
    public ResponseEntity<RiderMeasurement> create(@RequestParam(defaultValue = "1") Long userId,
                                                   @RequestBody RiderMeasurement request) {
        if (!valid(request)) return ResponseEntity.badRequest().build();
        request.setId(null);
        request.setUserId(userId);
        return ResponseEntity.ok(repository.save(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RiderMeasurement> update(@PathVariable Long id, @RequestBody RiderMeasurement request) {
        return repository.findById(id)
                .map(existing -> {
                    if (!valid(request)) return ResponseEntity.badRequest().<RiderMeasurement>build();
                    request.setId(existing.getId());
                    request.setUserId(existing.getUserId());
                    return ResponseEntity.ok(repository.save(request));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static boolean valid(RiderMeasurement r) {
        return r.getMeasureDate() != null && (r.getArchType() == null || ARCH_TYPES.contains(r.getArchType()));
    }
}

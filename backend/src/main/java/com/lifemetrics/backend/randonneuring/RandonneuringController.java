package com.lifemetrics.backend.randonneuring;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.RandonneuringResult;
import com.lifemetrics.backend.repository.RandonneuringResultRepository;
import com.lifemetrics.backend.service.RandonneuringCareer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 랜도너스 공식 참가 기록. 조회는 공개(라이딩 기록과 같은 수준), 가져오기(POST)는 AdminWriteFilter 가 관리자만 허용.
 */
@RestController
@RequestMapping("/api/randonneuring")
@RequiredArgsConstructor
public class RandonneuringController {

    static final long USER_ID = 1L;

    private final RandonneuringResultRepository repo;
    private final RandonneuringImportService importService;

    public record Summary(String career, List<RandonneuringResult> items) {}

    @GetMapping("/results")
    public Summary results() {
        List<RandonneuringResult> all = repo.findByUserIdOrderByEventDateDescIdDesc(USER_ID);
        return new Summary(RandonneuringCareer.summary(all), all);
    }

    /** KORA 기록 API 응답 JSON 을 그대로 받는다 ({"items":[…]} 또는 배열). */
    @PostMapping("/import")
    public ResponseEntity<?> importKora(@RequestBody JsonNode body) {
        try {
            return ResponseEntity.ok(importService.importKora(USER_ID, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

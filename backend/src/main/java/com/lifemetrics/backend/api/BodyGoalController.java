package com.lifemetrics.backend.api;

import com.lifemetrics.backend.entity.BodyGoal;
import com.lifemetrics.backend.service.BodyGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 체성분 목표. 쓰기(POST/PUT)는 AdminWriteFilter 가 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/body/goals")
@RequiredArgsConstructor
public class BodyGoalController {

    private final BodyGoalService goalService;

    /** 최신 목표부터 */
    @GetMapping
    public List<BodyGoal> list(@RequestParam(defaultValue = "1") Long userId) {
        return goalService.list(userId);
    }

    /** 진행 중인 목표 현황. 목표가 없으면 204. */
    @GetMapping("/current")
    public ResponseEntity<?> current(@RequestParam(defaultValue = "1") Long userId) {
        return goalService.status(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** 최신 인바디 기록으로 만든 목표 제안. 저장하지 않는다. */
    @GetMapping("/suggestion")
    public ResponseEntity<?> suggestion(@RequestParam(defaultValue = "1") Long userId) {
        try {
            return ResponseEntity.ok(goalService.suggest(userId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestParam(defaultValue = "1") Long userId,
                                    @RequestBody BodyGoal request) {
        try {
            return ResponseEntity.ok(goalService.create(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody BodyGoal request) {
        try {
            return goalService.update(id, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 진행 중인 목표에 대한 AI 코칭. 호출 비용이 있어 화면에서 버튼으로만 부른다. */
    @PostMapping("/ai")
    public ResponseEntity<?> narrative(@RequestParam(defaultValue = "1") Long userId) {
        try {
            String narrative = goalService.generateNarrative(userId);
            if (narrative == null) {
                return ResponseEntity.status(503)
                        .body(Map.of("message", "AI 분석을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."));
            }
            return ResponseEntity.ok(Map.of("narrative", narrative));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
        }
    }
}

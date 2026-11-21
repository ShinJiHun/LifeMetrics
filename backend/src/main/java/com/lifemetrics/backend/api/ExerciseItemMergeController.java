package com.lifemetrics.backend.api;

import com.lifemetrics.backend.security.AdminWriteFilter;
import com.lifemetrics.backend.service.ExerciseItemMergeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 중복 운동 종목 병합 (관리자 전용).
 * GET  /api/exercise/items/duplicates?exclude=1,2      병합 미리보기
 * POST /api/exercise/items/merge-duplicates {"excludeIds":[..]}  병합 실행 (AdminWriteFilter 가 관리자 검사)
 */
@RestController
@RequestMapping("/api/exercise/items")
@RequiredArgsConstructor
public class ExerciseItemMergeController {

    private final ExerciseItemMergeService service;
    private final AdminWriteFilter adminWriteFilter;

    public record MergeRequest(List<Long> excludeIds) {}

    @GetMapping("/duplicates")
    public ExerciseItemMergeService.Preview duplicates(@RequestParam(required = false) List<Long> exclude, HttpServletRequest req) {
        if (!adminWriteFilter.isAdmin(req)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        return service.preview(exclude);
    }

    @PostMapping("/merge-duplicates")
    public ExerciseItemMergeService.Result merge(@RequestBody(required = false) MergeRequest body) {
        return service.merge(body == null ? null : body.excludeIds());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }
}

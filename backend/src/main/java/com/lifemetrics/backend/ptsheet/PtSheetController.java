package com.lifemetrics.backend.ptsheet;

import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * PT 운동일지 구글 시트 → 운동 기록 수동 동기화 (관리자 전용).
 * POST 는 AdminWriteFilter 가 관리자 쿠키를 검사하고, 마지막 결과 조회(GET)도 여기서 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/exercise/pt-sheet")
@RequiredArgsConstructor
public class PtSheetController {

    private final PtSheetSyncService service;
    private final AdminWriteFilter adminWriteFilter;

    @PostMapping("/sync")
    public PtSheetSyncService.Result sync() {
        return service.sync();
    }

    @GetMapping("/last")
    public ResponseEntity<PtSheetSyncService.Result> last(HttpServletRequest req) {
        if (!adminWriteFilter.isAdmin(req)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        return service.last().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }
}

package com.lifemetrics.backend.persona.worklog;

import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 업무 일지. 개인 업무 기록이므로 조회(GET)도 관리자만. */
@RestController
@RequestMapping("/api/worklog")
@RequiredArgsConstructor
public class WorkLogController {

    private final WorkLogService service;
    private final AdminWriteFilter adminWriteFilter;

    public record ChatRequest(String message) {}
    public record EntryRequest(String todayDone, String tomorrowPlan, String issues) {}
    public record WeeklyRequest(String doneSummary, String nextPlan, String issues) {}

    @GetMapping
    public List<WorkLogService.Entry> list(@RequestParam String from, @RequestParam String to, HttpServletRequest req) {
        requireAdmin(req);
        return service.list(LocalDate.parse(from), LocalDate.parse(to));
    }

    @GetMapping("/{date}")
    public WorkLogService.Day day(@PathVariable String date, HttpServletRequest req) {
        requireAdmin(req);
        return service.day(LocalDate.parse(date));
    }

    @PostMapping("/{date}/chat")
    public WorkLogService.ChatResult chat(@PathVariable String date, @RequestBody ChatRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return service.chat(LocalDate.parse(date), body.message());
    }

    @PutMapping("/{date}")
    public WorkLogService.Entry save(@PathVariable String date, @RequestBody EntryRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return service.save(LocalDate.parse(date), body.todayDone(), body.tomorrowPlan(), body.issues());
    }

    @GetMapping("/weekly/{weekStart}")
    public WorkLogService.Weekly weekly(@PathVariable String weekStart, HttpServletRequest req) {
        requireAdmin(req);
        return service.weekly(LocalDate.parse(weekStart));
    }

    @PostMapping("/weekly/{weekStart}/generate")
    public WorkLogService.Weekly generate(@PathVariable String weekStart, HttpServletRequest req) {
        requireAdmin(req);
        return service.generateWeekly(LocalDate.parse(weekStart));
    }

    @PutMapping("/weekly/{weekStart}")
    public WorkLogService.Weekly saveWeekly(@PathVariable String weekStart, @RequestBody WeeklyRequest body,
                                            HttpServletRequest req) {
        requireAdmin(req);
        return service.saveWeekly(LocalDate.parse(weekStart), body.doneSummary(), body.nextPlan(), body.issues());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}

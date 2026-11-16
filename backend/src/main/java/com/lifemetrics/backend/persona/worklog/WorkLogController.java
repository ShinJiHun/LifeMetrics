package com.lifemetrics.backend.persona.worklog;

import com.fasterxml.jackson.databind.JsonNode;
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
    private final WorkLogUploadService uploadService;
    private final WorkLogReportService reportService;
    private final WorkLogCareerService careerService;
    private final AdminWriteFilter adminWriteFilter;

    public record ChatRequest(String message) {}
    public record EntryRequest(String todayDone, String tomorrowPlan, String issues) {}
    public record WeeklyRequest(String doneSummary, String nextPlan, String issues) {}
    public record UploadRequest(String fileName, String content) {}

    // ── JSON 파일 업로드 (원본 보관) ── /{date} 보다 먼저 선언(경로 구분은 Spring 이 리터럴 우선으로 처리)
    @GetMapping("/uploads")
    public List<WorkLogUploadService.Upload> uploads(HttpServletRequest req) {
        requireAdmin(req);
        return uploadService.list();
    }

    @GetMapping("/uploads/{id}")
    public WorkLogUploadService.UploadDetail upload(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        return uploadService.get(id);
    }

    @PostMapping("/uploads")
    public WorkLogUploadService.UploadResult addUpload(@RequestBody UploadRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return uploadService.saveAndParse(body.fileName(), body.content());
    }

    @PostMapping("/uploads/{id}/parse")
    public WorkLogReportService.ParseResult reparse(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        return uploadService.reparse(id);
    }

    // ── 파싱된 보고서 (업로드 JSON 을 날짜별로 풀어 저장한 것) ──
    @GetMapping("/reports")
    public List<WorkLogReportService.ReportSummary> reports(@RequestParam(required = false) String kind,
                                                            HttpServletRequest req) {
        requireAdmin(req);
        return reportService.list(kind);
    }

    @GetMapping("/reports/topics")
    public List<WorkLogReportService.TopicGroup> reportTopics(HttpServletRequest req) {
        requireAdmin(req);
        return reportService.topics();
    }

    // ── 경력기술서 초안 (보고서 → AI 초안 → 승인 시 career_* 반영) ──
    @GetMapping("/career-drafts")
    public List<WorkLogCareerService.DraftSummary> careerDrafts(HttpServletRequest req) {
        requireAdmin(req);
        return careerService.list();
    }

    @PostMapping("/career-drafts")
    public WorkLogCareerService.Draft generateCareerDraft(HttpServletRequest req) {
        requireAdmin(req);
        return careerService.generate("manual");
    }

    @GetMapping("/career-drafts/{id}")
    public WorkLogCareerService.Draft careerDraft(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        return careerService.get(id);
    }

    @PutMapping("/career-drafts/{id}")
    public WorkLogCareerService.Draft updateCareerDraft(@PathVariable long id, @RequestBody JsonNode draft,
                                                        HttpServletRequest req) {
        requireAdmin(req);
        return careerService.update(id, draft);
    }

    @PostMapping("/career-drafts/{id}/apply")
    public WorkLogCareerService.Draft applyCareerDraft(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        return careerService.apply(id);
    }

    @DeleteMapping("/career-drafts/{id}")
    public void discardCareerDraft(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        careerService.discard(id);
    }

    @GetMapping("/reports/{kind}/{date}")
    public WorkLogReportService.ReportDetail report(@PathVariable String kind, @PathVariable String date,
                                                    HttpServletRequest req) {
        requireAdmin(req);
        return reportService.get(kind, LocalDate.parse(date));
    }

    @DeleteMapping("/uploads/{id}")
    public void deleteUpload(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        uploadService.delete(id);
    }

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

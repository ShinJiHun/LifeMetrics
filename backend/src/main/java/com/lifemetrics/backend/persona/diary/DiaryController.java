package com.lifemetrics.backend.persona.diary;

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

/** 휴먼 일기. 개인 메모라 조회(GET)도 관리자만. */
@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService service;
    private final AdminWriteFilter adminWriteFilter;

    public record NoteRequest(String source, String fileName, String content) {}
    public record EntryRequest(String title, String content) {}
    public record PublishRequest(Long subId, String visibility) {}

    @GetMapping("/recent")
    public List<Map<String, Object>> recent(HttpServletRequest req) {
        requireAdmin(req);
        return service.recent(60);
    }

    @GetMapping("/sub-menus")
    public List<DiaryService.SubMenu> subMenus(HttpServletRequest req) {
        requireAdmin(req);
        return service.subMenus();
    }

    @GetMapping("/{date}")
    public DiaryService.Day day(@PathVariable String date, HttpServletRequest req) {
        requireAdmin(req);
        return service.day(LocalDate.parse(date));
    }

    @PostMapping("/{date}/notes")
    public DiaryService.Note addNote(@PathVariable String date, @RequestBody NoteRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return service.addNote(LocalDate.parse(date), body.source(), body.fileName(), body.content());
    }

    @DeleteMapping("/notes/{id}")
    public void deleteNote(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        service.deleteNote(id);
    }

    /** 초안 만들기·다시 만들기. 사람이 고친 초안도 덮어쓴다(화면에서 확인을 받는다). */
    @PostMapping("/{date}/generate")
    public DiaryService.Day generate(@PathVariable String date, HttpServletRequest req) {
        requireAdmin(req);
        LocalDate d = LocalDate.parse(date);
        return service.generate(d, true).orElseGet(() -> service.day(d));
    }

    @PutMapping("/entries/{id}")
    public DiaryService.Entry update(@PathVariable long id, @RequestBody EntryRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return service.update(id, body.title(), body.content());
    }

    @DeleteMapping("/entries/{id}")
    public void discard(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        service.discard(id);
    }

    @PostMapping("/entries/{id}/publish")
    public DiaryService.Entry publish(@PathVariable long id, @RequestBody PublishRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return service.publish(id, body.subId(), body.visibility());
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

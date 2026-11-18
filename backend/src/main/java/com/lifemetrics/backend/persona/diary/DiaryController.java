package com.lifemetrics.backend.persona.diary;

import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 휴먼 일기. 개인 메모라 조회(GET)도 관리자만.
 * 인증: 웹은 관리자 쿠키, 폰 앱은 {@code Authorization: Bearer <HEALTH_SYNC_TOKEN>}(헬스 동기화와 같은 앱 토큰).
 * 둘 다 없거나 틀리면 403 {"message":"관리자만 접근할 수 있습니다."} — 앱은 이 응답을 보고 로그인 창을 띄운다.
 * AdminWriteFilter 는 이 경로를 쿠키로 막지 않고 여기에 맡긴다.
 */
@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService service;
    private final AdminWriteFilter adminWriteFilter;

    @Value("${HEALTH_SYNC_TOKEN:}")
    private String appToken;

    public record NoteRequest(String source, String fileName, String content) {}
    public record EntryRequest(String title, String content) {}
    public record PublishRequest(Long subId, String visibility) {}

    @GetMapping("/recent")
    public List<Map<String, Object>> recent(HttpServletRequest req) {
        requireAdmin(req);
        return service.recent(60);
    }

    @GetMapping("/sub-menus")
    public DiaryService.SubMenus subMenus(HttpServletRequest req) {
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
        if (adminWriteFilter.isAdmin(request) || hasValidAppToken(request)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
    }

    private boolean hasValidAppToken(HttpServletRequest request) {
        if (appToken == null || appToken.isBlank()) return false;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        return MessageDigest.isEqual(appToken.getBytes(StandardCharsets.UTF_8),
                header.substring("Bearer ".length()).trim().getBytes(StandardCharsets.UTF_8));
    }
}

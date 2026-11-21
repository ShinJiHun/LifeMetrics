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
    private final LectureService lectures;
    private final AdminWriteFilter adminWriteFilter;

    @Value("${HEALTH_SYNC_TOKEN:}")
    private String appToken;

    public record LectureRequest(String title, String speaker, String content) {}
    public record TagsRequest(List<String> tags) {}
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

    // ── 강연 요약 ────────────────────────────────────

    /** 그날 메모에서 강연 필기를 찾아 요약 초안을 만든다. 일기 [지금 만들기] 전에 화면이 먼저 부른다. */
    @PostMapping("/{date}/lectures/generate")
    public LectureService.ScanResult generateLectures(@PathVariable String date, HttpServletRequest req) {
        requireAdmin(req);
        return service.generateLectures(LocalDate.parse(date));
    }

    /** 메모 하나를 강연 필기로 보고 요약을 (다시) 만든다. */
    @PostMapping("/notes/{id}/lecture")
    public LectureService.Lecture summarizeNote(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        return lectures.summarizeNote(id);
    }

    @GetMapping("/lectures")
    public List<LectureService.Lecture> lectures(@RequestParam(required = false) String tag,
                                                 @RequestParam(required = false) String speaker,
                                                 @RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to,
                                                 HttpServletRequest req) {
        requireAdmin(req);
        return lectures.list(tag, speaker, parseDate(from), parseDate(to));
    }

    @GetMapping("/lectures/facets")
    public LectureService.Facets lectureFacets(HttpServletRequest req) {
        requireAdmin(req);
        return lectures.facets();
    }

    @PutMapping("/lectures/{id}")
    public LectureService.Lecture updateLecture(@PathVariable long id, @RequestBody LectureRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return lectures.update(id, body.title(), body.speaker(), body.content());
    }

    @PutMapping("/lectures/{id}/tags")
    public LectureService.Lecture setLectureTags(@PathVariable long id, @RequestBody TagsRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return lectures.setTags(id, body.tags());
    }

    @DeleteMapping("/lectures/{id}")
    public void discardLecture(@PathVariable long id, HttpServletRequest req) {
        requireAdmin(req);
        lectures.discard(id);
    }

    @PostMapping("/lectures/{id}/publish")
    public LectureService.Lecture publishLecture(@PathVariable long id, @RequestBody PublishRequest body, HttpServletRequest req) {
        requireAdmin(req);
        return service.publishLecture(id, body.subId(), body.visibility());
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.strip());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "날짜 형식이 올바르지 않습니다: " + s);
        }
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

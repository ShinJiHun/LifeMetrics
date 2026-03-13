package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.BrevetCourseDto;
import com.lifemetrics.backend.dto.BrevetDto;
import com.lifemetrics.backend.service.BrevetGpxService;
import com.lifemetrics.backend.service.BrevetScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/brevet")
@RequiredArgsConstructor
public class BrevetController {

    private final BrevetScheduleService brevetScheduleService;
    private final BrevetGpxService brevetGpxService;

    // ── 기존: 브레베 일정 ──────────────────────────────

    /** GET /api/brevet/schedule */
    @GetMapping("/schedule")
    public List<BrevetDto> getSchedule() throws IOException {
        return brevetScheduleService.fetchSchedule();
    }

    /** GET /api/brevet/schedule/filter?distance=200&region=서울 */
    @GetMapping("/schedule/filter")
    public List<BrevetDto> getByDistance(
            @RequestParam(required = false) String distance,
            @RequestParam(required = false) String region
    ) throws IOException {
        return brevetScheduleService.fetchSchedule().stream()
                .filter(b -> distance == null || b.getDistance().equals(distance))
                .filter(b -> region == null || b.getRegion().contains(region))
                .toList();
    }

    // ── 신규: NAS GPX 코스 목록 / 파일 서빙 ──────────────

    /**
     * NAS에 저장된 브레베 코스 목록
     * GET /api/brevet/courses
     */
    @GetMapping("/courses")
    public List<BrevetCourseDto> getCourses() throws IOException {
        return brevetGpxService.listCourses();
    }

    /**
     * GPX 파일 내용 반환 (XML 문자열)
     * GET /api/brevet/courses/{folderName}/gpx?file=xxx.gpx
     */
    @GetMapping(value = "/courses/{folderName}/gpx", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getGpx(
            @PathVariable String folderName,
            @RequestParam String file
    ) {
        try {
            String content = brevetGpxService.getGpxContent(folderName, file);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(content);
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

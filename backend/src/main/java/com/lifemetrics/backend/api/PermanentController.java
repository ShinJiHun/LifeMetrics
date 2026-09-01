package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.PermanentCourseDto;
import com.lifemetrics.backend.entity.PermanentCourse;
import com.lifemetrics.backend.repository.PermanentCourseRepository;
import com.lifemetrics.backend.service.PermanentGpxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 퍼머넌트(Permanent) 코스 목록 API.
 * permanent_courses 테이블은 data_pipeline의 update_permanents.py 배치(월 1회)가
 * ebrevet API 기준으로 동기화한다. 여기서는 조회만 한다.
 * gpx 파일은 DB에 저장하지 않고 NAS의 permanent_no 폴더를 그때그때 스캔한다.
 */
@RestController
@RequestMapping("/api/permanents")
@RequiredArgsConstructor
public class PermanentController {

    private final PermanentCourseRepository permanentCourseRepository;
    private final PermanentGpxService permanentGpxService;

    /**
     * GET /api/permanents — 활성 코스 × gpx 파일 조합, permanent_no 순.
     * 폴더에 gpx가 여러 개인 코스(본코스/Plan B 등)는 permanentNo가 같은 행으로 여러 개 나온다.
     */
    @GetMapping
    public List<PermanentCourseDto> getActiveCourses() {
        List<PermanentCourse> courses = permanentCourseRepository.findByIsActiveTrueOrderByPermanentNoAsc();
        List<PermanentCourseDto> result = new ArrayList<>();

        for (PermanentCourse c : courses) {
            List<String> gpxFiles = permanentGpxService.listGpxFiles(c.getPermanentNo());
            if (gpxFiles.isEmpty()) {
                result.add(PermanentCourseDto.from(c, null));
            } else {
                for (String gpxFile : gpxFiles) {
                    result.add(PermanentCourseDto.from(c, gpxFile));
                }
            }
        }
        return result;
    }

    /** GET /api/permanents/{permanentNo}/gpx?file=xxx.gpx — NAS의 GPX 파일 내용 반환 */
    @GetMapping(value = "/{permanentNo}/gpx", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getGpx(
            @PathVariable String permanentNo,
            @RequestParam String file
    ) {
        try {
            String content = permanentGpxService.getGpxContent(permanentNo, file);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(content);
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/permanents/{permanentNo}/polyline/refresh — 대표 gpx(폴더에 여러 개면 첫 번째)로
     * polyline을 다시 계산해 저장한다. NAS가 마운트된 prod에서만 의미 있다.
     */
    @PostMapping("/{permanentNo}/polyline/refresh")
    public ResponseEntity<String> refreshPolyline(@PathVariable String permanentNo) {
        PermanentCourse course = permanentCourseRepository.findById(permanentNo).orElse(null);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }

        Optional<String> polyline = permanentGpxService.computePolylineForPrimaryFile(permanentNo);
        if (polyline.isEmpty()) {
            return ResponseEntity.ok("gpx 없음 또는 파싱 실패: " + permanentNo);
        }
        course.setPolyline(polyline.get());
        permanentCourseRepository.save(course);
        return ResponseEntity.ok("polyline 갱신 완료: " + permanentNo);
    }

    /** POST /api/permanents/polyline/refresh-all — 활성 코스 전체 일괄 재계산 (배포 후 최초 1회 호출용) */
    @PostMapping("/polyline/refresh-all")
    public ResponseEntity<String> refreshAllPolylines() {
        List<PermanentCourse> courses = permanentCourseRepository.findByIsActiveTrueOrderByPermanentNoAsc();
        int updated = 0;
        int skipped = 0;
        for (PermanentCourse c : courses) {
            Optional<String> polyline = permanentGpxService.computePolylineForPrimaryFile(c.getPermanentNo());
            if (polyline.isPresent()) {
                c.setPolyline(polyline.get());
                permanentCourseRepository.save(c);
                updated++;
            } else {
                skipped++;
            }
        }
        return ResponseEntity.ok(String.format(
                "polyline 갱신 완료: %d개 성공, %d개 건너뜀(gpx 없음/파싱 실패)", updated, skipped));
    }
}

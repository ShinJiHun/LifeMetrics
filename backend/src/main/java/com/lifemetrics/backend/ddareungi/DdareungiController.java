package com.lifemetrics.backend.ddareungi;

import com.lifemetrics.backend.notify.SlackNotifier;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ddareungi")
@RequiredArgsConstructor
public class DdareungiController {

    private final DdareungiImportService importService;
    private final DdareungiStationService stationService;
    private final SlackNotifier slackNotifier;
    private final DdareungiJobService jobService;
    private final AdminWriteFilter adminWriteFilter;

    /**
     * 비동기 업로드: 파일을 받아 작업 id 만 바로 돌려준다. 결과는 GET /upload/jobs/{id} 로 폴링.
     * (동기 /upload 는 인식이 오래 걸리면 모바일 회선에서 연결이 끊겨 "Failed to fetch" 가 난다.)
     */
    @PostMapping("/upload/jobs")
    public ResponseEntity<DdareungiJobService.Job> submitJob(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "false") boolean dryRun) throws IOException {
        List<DdareungiJobService.ImageInput> images = new ArrayList<>();
        for (MultipartFile f : files) {
            images.add(new DdareungiJobService.ImageInput(f.getOriginalFilename(), f.getContentType(), f.getBytes()));
        }
        return ResponseEntity.accepted().body(jobService.submit(images, userId, dryRun));
    }

    /** 작업 상태·결과. 결과에 기록 내용이 들어 있어 관리자만 본다. */
    @GetMapping("/upload/jobs/{jobId}")
    public ResponseEntity<DdareungiJobService.Job> getJob(HttpServletRequest request, @PathVariable String jobId) {
        if (!adminWriteFilter.isAdmin(request)) return ResponseEntity.status(403).build();
        return jobService.get(jobId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * 따릉이 이용내역 캡처(여러 장 가능)를 읽어 활동으로 저장한다.
     * dryRun=true 면 저장하지 않고 인식 결과(추정 거리·칼로리·경로 여부)만 돌려준다.
     */
    @PostMapping("/upload")
    public ResponseEntity<List<DdareungiImportService.FileResult>> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        List<DdareungiImportService.FileResult> results = files.stream()
                .map(f -> importService.importImage(f, userId, dryRun))
                .toList();
        if (!dryRun) {
            long saved = results.stream().flatMap(r -> r.rides().stream())
                    .filter(r -> "saved".equals(r.status())).count();
            if (saved > 0) slackNotifier.send("🚲 *따릉이 기록 " + saved + "건 추가*");
        }
        return ResponseEntity.ok(results);
    }

    /** 서울 열린데이터 bikeList 로 대여소 좌표 캐시를 갱신한다. */
    @PostMapping("/stations/sync")
    public ResponseEntity<Map<String, Object>> syncStations() {
        try {
            return ResponseEntity.ok(Map.of("success", true, "count", stationService.syncStations()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

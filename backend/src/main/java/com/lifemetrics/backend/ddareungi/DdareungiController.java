package com.lifemetrics.backend.ddareungi;

import com.lifemetrics.backend.notify.SlackNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ddareungi")
@RequiredArgsConstructor
public class DdareungiController {

    private final DdareungiImportService importService;
    private final DdareungiStationService stationService;
    private final SlackNotifier slackNotifier;

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

package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.BodyRecordsResponse;
import com.lifemetrics.backend.dto.WeightRequest;
import com.lifemetrics.backend.service.BodyService;
import com.lifemetrics.backend.service.FitdaysOcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/body")
@RequiredArgsConstructor
public class BodyController {

    private final BodyService bodyService;
    private final FitdaysOcrService fitdaysOcrService;

    // 신체 기록 조회
    @GetMapping("/records")
    public BodyRecordsResponse getBodyRecords(
            @RequestParam(defaultValue = "1") Long userId) {
        return bodyService.getBodyRecords(userId);
    }

    // InBody 이미지 업로드 (기존 파이프라인 - NAS 저장)
    @PostMapping("/inbody/upload")
    public ResponseEntity<Map<String, Object>> uploadInbodyImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "INBODY") String type) {
        return bodyService.uploadImage(file, type);
    }

    // FitDays 이미지 업로드 (즉시 OCR → DB 저장)
    @PostMapping("/fitdays/upload")
    public ResponseEntity<Map<String, Object>> uploadFitdays(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {
        Map<String, Object> result = fitdaysOcrService.processImage(file, userId);
        return ResponseEntity.ok(result);
    }

    // 체중 수동 입력
    @PostMapping("/weight")
    public ResponseEntity<?> addWeight(@RequestBody WeightRequest req) {
        bodyService.saveWeight(req);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

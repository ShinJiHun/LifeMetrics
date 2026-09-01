package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.BodyRecordsResponse;
import com.lifemetrics.backend.dto.WeightLossAnalysisResponse;
import com.lifemetrics.backend.dto.WeightRequest;
import com.lifemetrics.backend.service.BodyRecordAnalysisService;
import com.lifemetrics.backend.service.BodyService;
import com.lifemetrics.backend.service.FitdaysOcrService;
import com.lifemetrics.backend.service.InbodyReExtractService;
import com.lifemetrics.backend.service.WeightLossAnalysisService;
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
    private final WeightLossAnalysisService weightLossAnalysisService;
    private final InbodyReExtractService inbodyReExtractService;
    private final BodyRecordAnalysisService bodyRecordAnalysisService;

    // 신체 기록 조회
    @GetMapping("/records")
    public BodyRecordsResponse getBodyRecords(
            @RequestParam(defaultValue = "1") Long userId) {
        return bodyService.getBodyRecords(userId);
    }

    // 감량 분석: 기초대사량만 먹을 때 체지방 1kg 감량에 필요한 라이딩 양
    @GetMapping("/weight-loss")
    public ResponseEntity<?> getWeightLossAnalysis(
            @RequestParam(defaultValue = "1") Long userId) {
        try {
            return ResponseEntity.ok(weightLossAnalysisService.analyze(userId));
        } catch (IllegalStateException e) {
            // 인바디 기록이나 파워 데이터가 없는 경우 — 화면에서 안내 문구로 쓴다.
            return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
        }
    }

    // 감량 분석 AI 코칭. 호출 비용이 있어 화면에서 버튼으로만 부른다.
    @PostMapping("/weight-loss/ai")
    public ResponseEntity<?> getWeightLossNarrative(
            @RequestParam(defaultValue = "1") Long userId) {
        try {
            String narrative = weightLossAnalysisService.generateNarrative(userId);
            if (narrative == null) {
                // 키 미설정과 API 호출 실패가 모두 null 로 오므로 원인은 서버 로그에서 확인한다.
                return ResponseEntity.status(503)
                        .body(Map.of("message", "AI 분석을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."));
            }
            return ResponseEntity.ok(Map.of("narrative", narrative));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * NAS 기록지 재추출. 기본은 dry-run 이라 차이만 돌려주고 저장하지 않는다.
     * 덮어쓰려면 apply=true 를 명시해야 한다. 호출 비용이 있어 limit 은 필수다.
     */
    @PostMapping("/inbody/re-extract")
    public ResponseEntity<?> reExtractInbody(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "false") boolean apply,
            @RequestParam(defaultValue = "1") int limit) {
        return ResponseEntity.ok(inbodyReExtractService.reExtract(userId, apply, limit));
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

    // 신체 기록 1건에 대한 Claude 분석 생성. 호출 비용이 있어 화면에서 버튼으로만 부른다.
    @PostMapping("/records/{id}/analyze")
    public ResponseEntity<?> analyzeBodyRecord(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long userId) {
        try {
            String result = bodyRecordAnalysisService.analyze(userId, id);
            if (result == null) {
                return ResponseEntity.status(503)
                        .body(Map.of("message", "AI 분석을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."));
            }
            return ResponseEntity.ok(Map.of("rawLlmJson", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    // 체중 수동 입력
    @PostMapping("/weight")
    public ResponseEntity<?> addWeight(@RequestBody WeightRequest req) {
        bodyService.saveWeight(req);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

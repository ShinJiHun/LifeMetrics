package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.SegmentPredictRequest;
import com.lifemetrics.backend.dto.SegmentPredictResponse;
import com.lifemetrics.backend.service.SegmentPredictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class SegmentPredictController {

    private final SegmentPredictService predictService;

    /**
     * 구간별 유사 기록 기반 완주 시간 예측
     * POST /api/activity/predict
     */
    @PostMapping("/predict")
    public SegmentPredictResponse predict(@RequestBody SegmentPredictRequest request) {
        return predictService.predict(request);
    }
}

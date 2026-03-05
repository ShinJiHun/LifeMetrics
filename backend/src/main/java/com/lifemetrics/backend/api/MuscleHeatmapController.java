package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.MuscleHeatmapResponse;
import com.lifemetrics.backend.service.MuscleHeatmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/muscle")
@RequiredArgsConstructor
public class MuscleHeatmapController {

    private final MuscleHeatmapService muscleHeatmapService;

    /**
     * 근육 히트맵 조회
     * GET /api/muscle/heatmap?userId=1&month=2026-02
     */
    @GetMapping("/heatmap")
    public MuscleHeatmapResponse getHeatmap(
            @RequestParam Long userId,
            @RequestParam String month) {
        return muscleHeatmapService.getHeatmap(userId, month);
    }
}
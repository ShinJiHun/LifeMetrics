package com.lifemetrics.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuscleHeatmapResponse {

    private List<MuscleScore> muscles;
    private String period;  // "2026-02" or "2026-02-18"

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MuscleScore {
        private Long muscleGroupId;
        private String nameKo;
        private String nameEn;
        private Long parentId;
        private String bodyPart;    // UPPER, LOWER, CORE
        private int totalSets;      // 해당 근육에 누적된 총 세트 수
        private double totalVolume; // 해당 근육에 누적된 총 볼륨 (weight * reps * activation)
        private int score;          // 0~100 정규화 점수 (히트맵 색상용)
    }
}
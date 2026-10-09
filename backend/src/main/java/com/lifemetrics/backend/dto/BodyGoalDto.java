package com.lifemetrics.backend.dto;

import com.lifemetrics.backend.entity.BodyGoal;

import java.time.LocalDate;

public final class BodyGoalDto {

    private BodyGoalDto() {
    }

    /**
     * 최신 인바디 기록으로 만든 목표 제안. 화면에서 폼에 채워 넣고 사용자가 고쳐서 저장한다.
     *
     * @param source            INBODY(기록지 조절값) 또는 FORMULA(계산)
     * @param heightCm          체중/BMI 로 역산한 키. BMI 가 없으면 null
     * @param standardWeight    표준 체중(BMI 22). 참고용
     * @param weeksAtHalfPercent 주당 체중의 0.5% 감량 페이스로 걸리는 주 수
     * @param weeksAtOnePercent  주당 1% 페이스
     * @param sheetTargetWeight 기록지 체중조절 영역의 적정체중. 아래 조절값과 함께 기록지에 인쇄된 값 그대로
     * @param canReadSheet      조절값이 없지만 원본 기록지 이미지가 있어 읽어올 수 있음
     */
    public record Suggestion(
            String source,
            Long baseRecordId,
            LocalDate baseRecordDate,
            Double startWeight,
            Double startFatMass,
            Double startMuscleMass,
            Double targetWeight,
            Double targetFatMass,
            Double targetMuscleMass,
            LocalDate targetDate,
            Double heightCm,
            Double standardWeight,
            Double weeksAtHalfPercent,
            Double weeksAtOnePercent,
            String basis,
            Double sheetTargetWeight,
            Double weightControl,
            Double fatControl,
            Double muscleControl,
            boolean canReadSheet
    ) {
    }

    /** 진행 중인 목표와 최신 측정값, 진행률, W/kg. */
    public record Status(
            BodyGoal goal,
            Current current,
            Progress progress,
            Integer ftp,
            boolean ftpEstimated,
            Double wkgStart,
            Double wkgCurrent,
            Double wkgTarget
    ) {
    }

    /**
     * 체중은 체중계 기록까지 포함한 최신값, 체지방·골격근은 인바디 최신값.
     * 체중계(FITDAYS)의 체성분은 정확도가 떨어져 진행률에 쓰지 않는다.
     */
    public record Current(
            LocalDate weightDate,
            Double weight,
            LocalDate inbodyDate,
            Double fatMass,
            Double muscleMass
    ) {
    }

    /** 시작값 → 목표값 중 얼마나 왔는지(%). 목표와 시작이 같거나 값이 없으면 null. */
    public record Progress(
            Double weightPct,
            Double fatPct,
            Double musclePct,
            Long daysElapsed,
            Long daysLeft
    ) {
    }
}

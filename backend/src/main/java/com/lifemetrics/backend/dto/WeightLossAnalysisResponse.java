package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * "기초대사량만큼 먹으면 1kg 빼는 데 라이딩을 얼마나 해야 하나" 분석 결과.
 *
 * 라이딩 기여분만 계산한다. 기초대사량만 먹으면 일상 활동(NEAT)만으로도 이미 적자라
 * 그 부분은 dailyDeficit* 로 따로 내려주고 라이딩 요구량에는 섞지 않는다.
 */
@Getter
@Builder
public class WeightLossAnalysisResponse {

    // ===== 체성분 (최신 인바디) =====
    private LocalDate recordDate;
    private Double weight;
    private Double bodyFatMass;
    private Double bodyFatPercentage;
    /** 제지방량(kg) = 체중 - 체지방량. 인바디 기록지의 제지방량과 일치한다. */
    private Double fatFreeMass;
    /** 기초대사량(kcal). 기록지 값이 있으면 그대로, 없으면 제지방량에서 산출한다. */
    private Double basalMetabolicRate;
    /** 기초대사량이 측정값인지 계산값인지. 화면에 근거를 밝히기 위한 플래그. */
    private boolean bmrEstimated;
    /** 측정일로부터 며칠 지났는지. 오래되면 화면에서 경고한다. */
    private Long recordAgeDays;

    // ===== 라이딩 소비 (파워미터 기록 기준) =====
    private Long rideCount;
    private Double rideHours;
    private Double rideKm;
    private Double avgSpeedKmh;
    /** 시간당 기계적 일(kJ/h). */
    private Double kjPerHour;

    // ===== 시나리오 =====
    /** 순소비 기준이 기본값. 나머지는 비교용. */
    private List<Scenario> scenarios;

    // ===== 일상 소비 적자 (참고) =====
    /** 라이딩을 전혀 안 해도 기초대사량만 먹으면 생기는 하루 적자(kcal). */
    private Double dailyDeficitKcal;
    /** 그 적자만으로 1kg 빠지는 데 걸리는 일수. */
    private Double dailyDeficitDaysPerKg;

    @Getter
    @Builder
    public static class Scenario {
        /** 예: "순소비 (기초대사 제외)" */
        private String label;
        /** 이 추정을 어떻게 구했는지 한 줄 설명. */
        private String basis;
        private Double kcalPerHour;
        private Double hoursPerKg;
        private Double kmPerKg;
        /** 기본 권장 시나리오 여부. */
        private boolean primary;
    }
}

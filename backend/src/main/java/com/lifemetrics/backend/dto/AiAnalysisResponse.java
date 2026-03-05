package com.lifemetrics.backend.dto;

import com.lifemetrics.backend.domain.BodyType;

import java.util.List;

/**
 * AI 분석 응답 DTO
 * - 인바디 데이터 기반 체형 분석 결과
 * - 3D 모델 렌더링용 파라미터 포함
 */
public record AiAnalysisResponse(

        String model,                 // ✅ 추가: 실제 사용된 AI 모델명 (gpt-4o, claude-sonnet-4-20250514 등)

        String summaryJson,           // 전체 분석 결과 JSON (누적 서사용)

        BodyType bodyType,            // "lean", "normal", "fit", "athlete", "overweight", "obese"
        String bodyTypeKorean,        // "마름", "표준", "탄탄", "운동선수", "과체중", "비만"
        double bodyTypeConfidence,    // 체형 분류 신뢰도 (0.0 ~ 1.0)

        BodyModelParams modelParams,

        AnalysisResult analysis,

        ChangeTracking changes,

        List<Recommendation> recommendations

) {
    /**
     * 3D 바디 모델 파라미터
     * - Shape Keys 블렌딩 또는 STAR β 파라미터로 사용
     */
    public record BodyModelParams(
            // Shape Keys 블렌딩 값 (0.0 ~ 1.0)
            double fatLevel,          // 체지방 레벨 (0=마름, 1=비만)
            double muscleLevel,       // 근육량 레벨 (0=적음, 1=보디빌더)
            double slimLevel,         // 슬림 정도 (0=벌크, 1=마름)

            // STAR/SMPL β 파라미터용 (선택적)
            double heightScale,       // 키 스케일
            double weightScale,       // 체중 스케일
            double chestScale,        // 가슴둘레 스케일
            double waistScale,        // 허리둘레 스케일
            double hipScale,          // 엉덩이둘레 스케일
            double shoulderScale,     // 어깨너비 스케일

            // 근육 디테일 강도 (Displacement Map용)
            double muscleDefinition   // 근육 선명도 (0=smooth, 1=선명)
    ) {
        /**
         * 인바디 데이터로부터 모델 파라미터 계산
         */
        public static BodyModelParams fromInbody(
                double bodyFatPercent,
                double muscleMassKg,
                double heightCm,
                double weightKg
        ) {
            // Fat level: 체지방률 15% = 0, 35% = 1
            double fatLevel = Math.max(0, Math.min(1, (bodyFatPercent - 15) / 20.0));

            // Muscle level: 근육량 25kg = 0, 45kg = 1
            double muscleLevel = Math.max(0, Math.min(1, (muscleMassKg - 25) / 20.0));

            // Slim level: BMI 기반 (BMI 25 = 0, BMI 17 = 1)
            double bmi = weightKg / Math.pow(heightCm / 100, 2);
            double slimLevel = Math.max(0, Math.min(1, (25 - bmi) / 8.0));

            // 근육 선명도: 체지방률 낮을수록 높음 (25% = 0, 8% = 1)
            double muscleDefinition = Math.max(0, Math.min(1, (25 - bodyFatPercent) / 17.0));

            return new BodyModelParams(
                    fatLevel,
                    muscleLevel,
                    slimLevel,
                    (heightCm - 175) / 10.0,   // 175cm 기준
                    (weightKg - 70) / 20.0,    // 70kg 기준
                    0.0,  // 가슴둘레 (인바디에 없으면 0)
                    0.0,  // 허리둘레
                    0.0,  // 엉덩이둘레
                    0.0,  // 어깨너비
                    muscleDefinition
            );
        }
    }

    /**
     * 분석 결과 상세
     */
    public record AnalysisResult(
            // 기본 지표
            double bmi,
            String bmiCategory,           // "저체중", "정상", "과체중", "비만"

            double bodyFatPercent,
            String bodyFatCategory,       // "낮음", "표준", "높음", "매우높음"

            double muscleMassKg,
            String muscleMassCategory,    // "부족", "표준", "우수", "매우우수"

            double skeletalMuscleKg,      // 골격근량
            double basalMetabolicRate,    // 기초대사량 (kcal)

            // 체형 균형
            double muscleToFatRatio,      // 근육/체지방 비율
            String balanceScore,          // "불균형", "보통", "균형", "매우균형"

            // 부위별 분석 (인바디 부위별 데이터 있을 경우)
            SegmentalAnalysis segmental
    ) {}

    /**
     * 부위별 근육/체지방 분석
     */
    public record SegmentalAnalysis(
            SegmentData leftArm,
            SegmentData rightArm,
            SegmentData trunk,
            SegmentData leftLeg,
            SegmentData rightLeg
    ) {}

    public record SegmentData(
            double muscleKg,
            double fatKg,
            String status    // "부족", "표준", "발달"
    ) {}

    /**
     * 이전 대비 변화
     */
    public record ChangeTracking(
            boolean hasPrevious,          // 이전 데이터 존재 여부

            double weightChange,          // 체중 변화 (kg)
            double bodyFatChange,         // 체지방률 변화 (%)
            double muscleMassChange,      // 근육량 변화 (kg)

            String overallTrend,          // "개선", "유지", "악화"
            String trendDescription,      // "근육량 증가, 체지방 감소로 체형이 개선되고 있습니다."

            int daysSinceLast             // 마지막 측정 이후 일수
    ) {
        public static ChangeTracking none() {
            return new ChangeTracking(false, 0, 0, 0, "없음", "이전 데이터가 없습니다.", 0);
        }
    }

    /**
     * 추천 사항
     */
    public record Recommendation(
            String category,      // "exercise", "diet", "lifestyle"
            String title,         // "유산소 운동 추가"
            String description,   // "주 3회 30분 이상의 유산소 운동을 권장합니다."
            int priority          // 1 = 높음, 2 = 중간, 3 = 낮음
    ) {}

    // ========== 편의 메서드 ==========

    /**
     * 체형 분류 (6개 모델 매핑용)
     */
    public static String classifyBodyType(double bodyFatPercent, double muscleMassKg, double bmi) {
        // 비만
        if (bodyFatPercent >= 30 || bmi >= 30) return "obese";

        // 과체중
        if (bodyFatPercent >= 25 || bmi >= 25) return "overweight";

        // 운동선수 (체지방 낮음 + 근육량 높음)
        if (bodyFatPercent < 15 && muscleMassKg >= 40) return "athlete";

        // 탄탄 (체지방 적당 + 근육량 높음)
        if (bodyFatPercent < 20 && muscleMassKg >= 35) return "fit";

        // 마름
        if (bmi < 18.5 || (bodyFatPercent < 15 && muscleMassKg < 30)) return "lean";

        // 표준
        return "normal";
    }

    /**
     * 빈 응답 생성 (에러 시)
     */
    public static AiAnalysisResponse empty() {
        return new AiAnalysisResponse(
                "unknown",           // ✅ 추가
                "{}",
                BodyType.NORMAL,
                "표준",
                0.0,
                new BodyModelParams(0.5, 0.5, 0.5, 0, 0, 0, 0, 0, 0, 0),
                null,
                ChangeTracking.none(),
                List.of()
        );
    }
}
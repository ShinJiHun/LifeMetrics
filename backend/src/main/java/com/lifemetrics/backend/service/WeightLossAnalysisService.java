package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.WeightLossAnalysisResponse;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.PowerRideStats;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 기초대사량만큼 먹는다는 가정에서 체지방 1kg 감량에 필요한 라이딩 양을 계산한다.
 *
 * 계산 근거:
 * - 제지방량 = 체중 - 체지방량 (인바디 기록지의 제지방량과 일치)
 * - 기초대사량 = 370 + 21.6 × 제지방량 (Katch-McArdle). 인바디도 같은 식을 쓴다.
 *   2026-06-01 기록지의 인쇄값 1720 kcal 과 계산값이 정확히 일치하는 것을 확인했다.
 * - 라이딩 소비는 파워미터 기록의 기계적 일에서 구한다.
 *   사이클링 효율이 약 24% 라서 일(kJ)과 총소비(kcal)가 대략 1:1 로 맞는다.
 */
@Service
@RequiredArgsConstructor
public class WeightLossAnalysisService {

    /** 체지방 1kg 당 열량(kcal). 지방조직 기준의 통상값. */
    private static final double KCAL_PER_KG_FAT = 7700.0;

    /** 인바디가 쓰는 기초대사량 식의 상수. */
    private static final double BMR_INTERCEPT = 370.0;
    private static final double BMR_PER_FFM_KG = 21.6;

    /**
     * 일상 활동 계수. 라이딩을 제외한 생활 대사로, 좌식 생활 기준의 통상값이다.
     * 여기서는 "라이딩 없이도 생기는 적자"를 보여주는 데만 쓰고 라이딩 요구량에는 넣지 않는다.
     */
    private static final double SEDENTARY_FACTOR = 1.25;

    private static final String AI_SYSTEM_PROMPT = """
            당신은 사이클링 코치다. 아래 체성분·라이딩 데이터를 근거로 감량 계획을 조언한다.

            지켜야 할 것:
            - 주어진 숫자만 근거로 삼는다. 없는 데이터를 지어내지 않는다.
            - 기초대사량만 먹는 계획은 적자가 크다는 점을 짚어준다.
            - 체중계 체중과 체지방은 다르다는 점을 전제로 말한다.
            - 의학적 처방이 아니라 참고 의견임을 밝힌다.
            - 한국어로, 400자 내외의 문단 2~3개. 목록이나 마크다운 없이 서술형으로 쓴다.
            """;

    private final UserBodyRecordRepository bodyRecordRepository;
    private final ActivityCoreRepository activityCoreRepository;
    private final ClaudeClient claudeClient;

    @Transactional(readOnly = true)
    public WeightLossAnalysisResponse analyze(Long userId) {
        UserBodyRecord record = bodyRecordRepository
                .findTopByUserIdAndMeasurementTypeOrderByRecordDateDesc(userId, MeasurementType.INBODY)
                .orElseThrow(() -> new IllegalStateException("인바디 측정 기록이 없습니다."));

        if (record.getWeight() == null || record.getBodyFatMass() == null) {
            throw new IllegalStateException("최신 인바디 기록에 체중 또는 체지방량이 없어 계산할 수 없습니다.");
        }

        // 저장된 제지방량을 우선한다. 없으면 항등식으로 채운다(기록지 값과 일치).
        double fatFreeMass = record.getFatFreeMass() != null
                ? record.getFatFreeMass()
                : record.getWeight() - record.getBodyFatMass();

        // 기록지에서 추출된 값이 있으면 우선한다. 인바디 기록은 대체로 비어 있어 계산으로 채운다.
        boolean bmrEstimated = record.getBasalMetabolicRate() == null;
        double bmr = bmrEstimated
                ? BMR_INTERCEPT + BMR_PER_FFM_KG * fatFreeMass
                : record.getBasalMetabolicRate();

        PowerRideStats stats = activityCoreRepository.getPowerRideStats(userId);
        if (stats == null || stats.getMovingSeconds() == null || stats.getMovingSeconds() == 0) {
            throw new IllegalStateException("파워미터 라이딩 기록이 없어 소비열량을 추정할 수 없습니다.");
        }

        double rideHours = stats.getMovingSeconds() / 3600.0;
        double rideKm = stats.getDistanceMeters() / 1000.0;
        double avgSpeedKmh = rideKm / rideHours;
        double kjPerHour = stats.getWorkJoules() / 1000.0 / rideHours;

        // 효율 24% 가정에서 일(kJ) ≈ 총소비(kcal). 여기서 안정시 대사를 빼면 라이딩 순증분이 된다.
        double grossKcalPerHour = kjPerHour;
        double restingKcalPerHour = bmr / 24.0;
        double netKcalPerHour = grossKcalPerHour - restingKcalPerHour;
        double reportedKcalPerHour = stats.getReportedCalories() / rideHours;

        List<WeightLossAnalysisResponse.Scenario> scenarios = new ArrayList<>();
        scenarios.add(scenario("순소비 (기초대사 제외)",
                "기계적 일에서 안정시 대사를 뺀 값. 가장 보수적이라 기본값으로 쓴다.",
                netKcalPerHour, avgSpeedKmh, true));
        scenarios.add(scenario("총소비 (kJ 1:1 환산)",
                "기계적 일을 그대로 kcal 로 환산. 라이딩 중 기초대사가 포함된다.",
                grossKcalPerHour, avgSpeedKmh, false));
        scenarios.add(scenario("기기 기록 calories",
                "기기가 남긴 값. 기계적 일의 1.3배 수준이라 근거가 불명확해 참고용이다.",
                reportedKcalPerHour, avgSpeedKmh, false));

        double dailyDeficit = bmr * SEDENTARY_FACTOR - bmr;

        return WeightLossAnalysisResponse.builder()
                .recordDate(record.getRecordDate())
                .weight(record.getWeight())
                .bodyFatMass(record.getBodyFatMass())
                .bodyFatPercentage(record.getBodyFatPercentage())
                .fatFreeMass(round(fatFreeMass, 1))
                .basalMetabolicRate(round(bmr, 0))
                .bmrEstimated(bmrEstimated)
                .recordAgeDays(ChronoUnit.DAYS.between(record.getRecordDate(), LocalDate.now()))
                .rideCount(stats.getRideCount())
                .rideHours(round(rideHours, 1))
                .rideKm(round(rideKm, 0))
                .avgSpeedKmh(round(avgSpeedKmh, 1))
                .kjPerHour(round(kjPerHour, 0))
                .scenarios(scenarios)
                .dailyDeficitKcal(round(dailyDeficit, 0))
                .dailyDeficitDaysPerKg(round(KCAL_PER_KG_FAT / dailyDeficit, 1))
                .build();
    }

    /**
     * 계산 결과를 근거로 코칭 문단을 생성한다.
     * API 키가 없거나 호출이 실패하면 null 을 돌려주고, 화면에서는 섹션을 감춘다.
     */
    @Transactional(readOnly = true)
    public String generateNarrative(Long userId) {
        WeightLossAnalysisResponse a = analyze(userId);
        WeightLossAnalysisResponse.Scenario primary = a.getScenarios().stream()
                .filter(WeightLossAnalysisResponse.Scenario::isPrimary)
                .findFirst()
                .orElse(a.getScenarios().get(0));

        String facts = """
                측정일: %s (%d일 전)
                체중 %.1fkg, 체지방량 %.1fkg, 체지방률 %.1f%%, 제지방량 %.1fkg
                기초대사량 %.0f kcal (제지방량 기준)

                라이딩(파워미터 기록 %d회, %.1f시간, %.0fkm)
                평균 %.1f km/h, 시간당 기계적 일 %.0f kJ/h
                순소비 기준 시간당 %.0f kcal

                기초대사량만 먹을 때 체지방 1kg 감량에 필요한 라이딩: %.1f시간 / %.0fkm
                라이딩을 하지 않아도 일상 활동만으로 하루 %.0f kcal 적자 (1kg까지 %.1f일)
                """.formatted(
                a.getRecordDate(), a.getRecordAgeDays(),
                a.getWeight(), a.getBodyFatMass(), a.getBodyFatPercentage(), a.getFatFreeMass(),
                a.getBasalMetabolicRate(),
                a.getRideCount(), a.getRideHours(), a.getRideKm(),
                a.getAvgSpeedKmh(), a.getKjPerHour(),
                primary.getKcalPerHour(),
                primary.getHoursPerKg(), primary.getKmPerKg(),
                a.getDailyDeficitKcal(), a.getDailyDeficitDaysPerKg());

        return claudeClient.complete(AI_SYSTEM_PROMPT, facts, 1000);
    }

    private WeightLossAnalysisResponse.Scenario scenario(
            String label, String basis, double kcalPerHour, double speedKmh, boolean primary) {
        double hours = KCAL_PER_KG_FAT / kcalPerHour;
        return WeightLossAnalysisResponse.Scenario.builder()
                .label(label)
                .basis(basis)
                .kcalPerHour(round(kcalPerHour, 0))
                .hoursPerKg(round(hours, 1))
                .kmPerKg(round(hours * speedKmh, 0))
                .primary(primary)
                .build();
    }

    private double round(double v, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(v * factor) / factor;
    }
}

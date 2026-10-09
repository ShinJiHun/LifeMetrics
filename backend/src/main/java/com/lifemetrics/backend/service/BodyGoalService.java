package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.BodyGoalDto;
import com.lifemetrics.backend.dto.RiderZoneSettingDto;
import com.lifemetrics.backend.entity.BodyGoal;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.repository.BodyGoalRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 체성분 목표. 목표 숫자는 규칙으로 정하고, AI 는 정해진 목표에 대한 해설만 쓴다.
 *
 * 목표 제안 우선순위:
 * 1. INBODY — 기록지의 체지방/근육 조절값. 인바디가 표준 체중·표준 체지방률로 계산한 값이다.
 * 2. FORMULA — 조절값이 없을 때(재추출 전 기록 등). 제지방량은 유지하고 체지방률만
 *    표준(15%)에 맞춘다. 사이클리스트는 근육량이 많아 BMI 22 표준 체중보다 이 쪽이 현실적이다.
 */
@Service
@RequiredArgsConstructor
public class BodyGoalService {

    /** 표준 체지방률(%). 인바디 남성 기준값. 성별 정보가 없어 남성 기준으로 고정한다. */
    static final double STANDARD_BODY_FAT_PCT = 15.0;

    /** 표준 체중 산출용 BMI. 인바디·대한비만학회 공통. */
    static final double STANDARD_BMI = 22.0;

    /** 목표일 제안에 쓰는 주당 감량 페이스(체중 대비). 근손실을 줄이는 통상 권장 범위의 하한. */
    static final double SAFE_WEEKLY_LOSS_RATIO = 0.005;
    static final double FAST_WEEKLY_LOSS_RATIO = 0.01;

    private static final String AI_SYSTEM_PROMPT = """
            당신은 사이클링 코치다. 아래 체성분 목표와 현재 진행 상황을 근거로 조언한다.

            지켜야 할 것:
            - 주어진 숫자만 근거로 삼는다. 없는 데이터를 지어내지 않는다.
            - 목표 숫자를 새로 정하지 않는다. 정해진 목표의 페이스·식단·훈련 방향만 말한다.
            - 체중보다 체지방량 변화를 주 지표로 본다. 골격근량은 유지 또는 증가가 목표다.
            - W/kg 변화가 라이딩에 주는 의미를 짚는다.
            - 의학적 처방이 아니라 참고 의견임을 밝힌다.
            - 한국어로, 400자 내외의 문단 2~3개. 목록이나 마크다운 없이 서술형으로 쓴다.
            """;

    private final BodyGoalRepository goalRepository;
    private final UserBodyRecordRepository bodyRecordRepository;
    private final RiderZoneSettingService riderZoneSettingService;
    private final ClaudeClient claudeClient;

    @Transactional(readOnly = true)
    public List<BodyGoal> list(Long userId) {
        return goalRepository.findByUserIdOrderByStartDateDescIdDesc(userId);
    }

    /** 최신 인바디 기록으로 목표를 제안한다. 저장하지 않는다. */
    @Transactional(readOnly = true)
    public BodyGoalDto.Suggestion suggest(Long userId) {
        UserBodyRecord r = bodyRecordRepository
                .findTopByUserIdAndMeasurementTypeOrderByRecordDateDesc(userId, MeasurementType.INBODY)
                .orElseThrow(() -> new IllegalStateException("인바디 측정 기록이 없습니다."));
        if (r.getWeight() == null || r.getBodyFatMass() == null) {
            throw new IllegalStateException("최신 인바디 기록에 체중 또는 체지방량이 없어 목표를 계산할 수 없습니다.");
        }

        double weight = r.getWeight();
        double fat = r.getBodyFatMass();
        Double muscle = r.getSkeletalMuscleMass();

        Double heightCm = null;
        Double standardWeight = null;
        if (r.getBmi() != null && r.getBmi() > 0) {
            double heightM = Math.sqrt(weight / r.getBmi());
            heightCm = round(heightM * 100, 1);
            standardWeight = round(STANDARD_BMI * heightM * heightM, 1);
        }

        String source;
        String basis;
        double targetWeight;
        double targetFat;
        Double targetMuscle;

        if (r.getFatControl() != null || r.getTargetWeight() != null) {
            source = "INBODY";
            double fatControl = r.getFatControl() != null ? r.getFatControl() : 0.0;
            double muscleControl = r.getMuscleControl() != null ? r.getMuscleControl() : 0.0;
            targetFat = fat + fatControl;
            targetMuscle = muscle != null ? muscle + muscleControl : null;
            targetWeight = r.getTargetWeight() != null
                    ? r.getTargetWeight()
                    : weight + (r.getWeightControl() != null ? r.getWeightControl() : fatControl + muscleControl);
            basis = "%s 인바디 기록지의 조절값 (지방 %+.1fkg, 근육 %+.1fkg)"
                    .formatted(r.getRecordDate(), fatControl, muscleControl);
        } else {
            source = "FORMULA";
            double fatFreeMass = r.getFatFreeMass() != null ? r.getFatFreeMass() : weight - fat;
            double ratio = STANDARD_BODY_FAT_PCT / 100.0;
            // 제지방량 유지 + 체지방률 15% → 목표 체중 = 제지방량 / (1 - 0.15)
            double fatAtStandard = fatFreeMass * ratio / (1 - ratio);
            targetFat = Math.min(fat, fatAtStandard);
            targetMuscle = muscle;
            targetWeight = fatFreeMass + targetFat;
            basis = "기록지 조절값이 없어 계산: 제지방량 %.1fkg 유지, 체지방률 %.0f%%"
                    .formatted(fatFreeMass, STANDARD_BODY_FAT_PCT);
        }

        double loss = Math.max(0, weight - targetWeight);
        Double weeksSafe = loss > 0 ? round(loss / (weight * SAFE_WEEKLY_LOSS_RATIO), 1) : null;
        Double weeksFast = loss > 0 ? round(loss / (weight * FAST_WEEKLY_LOSS_RATIO), 1) : null;
        LocalDate targetDate = weeksSafe != null
                ? LocalDate.now().plusWeeks((long) Math.ceil(weeksSafe))
                : null;

        return new BodyGoalDto.Suggestion(
                source, r.getId(), r.getRecordDate(),
                weight, fat, muscle,
                round(targetWeight, 1), round(targetFat, 1), targetMuscle != null ? round(targetMuscle, 1) : null,
                targetDate, heightCm, standardWeight, weeksSafe, weeksFast, basis);
    }

    /** 새 목표 저장. 진행 중인 목표는 하나만 두므로 기존 ACTIVE 는 ABANDONED 로 돌린다. */
    @Transactional
    public BodyGoal create(Long userId, BodyGoal request) {
        validate(request);
        goalRepository.findByUserIdAndStatus(userId, BodyGoal.ACTIVE)
                .forEach(g -> g.setStatus(BodyGoal.ABANDONED));
        request.setId(null);
        request.setUserId(userId);
        request.setStatus(BodyGoal.ACTIVE);
        if (request.getSource() == null) request.setSource("MANUAL");
        return goalRepository.save(request);
    }

    @Transactional
    public Optional<BodyGoal> update(Long id, BodyGoal request) {
        validate(request);
        return goalRepository.findById(id).map(existing -> {
            request.setId(existing.getId());
            request.setUserId(existing.getUserId());
            if (request.getStatus() == null) request.setStatus(existing.getStatus());
            if (request.getSource() == null) request.setSource(existing.getSource());
            return goalRepository.save(request);
        });
    }

    /** 진행 중인 목표의 현황. 목표가 없으면 empty. */
    @Transactional(readOnly = true)
    public Optional<BodyGoalDto.Status> status(Long userId) {
        return goalRepository.findTopByUserIdAndStatusOrderByIdDesc(userId, BodyGoal.ACTIVE)
                .map(goal -> buildStatus(userId, goal));
    }

    /**
     * 진행 중인 목표에 대한 코칭 문단. 목표가 없으면 IllegalStateException.
     * API 키가 없거나 호출이 실패하면 null.
     */
    @Transactional(readOnly = true)
    public String generateNarrative(Long userId) {
        BodyGoalDto.Status s = status(userId)
                .orElseThrow(() -> new IllegalStateException("진행 중인 체성분 목표가 없습니다."));
        BodyGoal g = s.goal();
        BodyGoalDto.Current c = s.current();
        BodyGoalDto.Progress p = s.progress();

        String facts = """
                목표 설정일 %s, 목표일 %s (경과 %s일, 남은 %s일)
                목표 근거: %s

                체중: 시작 %s → 현재 %s (%s 측정) → 목표 %s kg, 진행률 %s%%
                체지방량: 시작 %s → 현재 %s (%s 인바디) → 목표 %s kg, 진행률 %s%%
                골격근량: 시작 %s → 현재 %s → 목표 %s kg

                FTP %d W%s
                W/kg: 시작 %s → 현재 %s → 목표 체중 도달 시 %s
                """.formatted(
                g.getStartDate(), nv(g.getTargetDate()), nv(p.daysElapsed()), nv(p.daysLeft()),
                g.getSource(),
                nv(g.getStartWeight()), nv(c.weight()), nv(c.weightDate()), nv(g.getTargetWeight()), nv(p.weightPct()),
                nv(g.getStartFatMass()), nv(c.fatMass()), nv(c.inbodyDate()), nv(g.getTargetFatMass()), nv(p.fatPct()),
                nv(g.getStartMuscleMass()), nv(c.muscleMass()), nv(g.getTargetMuscleMass()),
                s.ftp(), s.ftpEstimated() ? " (설정값 없음, 체중×3.0 추정)" : "",
                nv(s.wkgStart()), nv(s.wkgCurrent()), nv(s.wkgTarget()));

        return claudeClient.complete(AI_SYSTEM_PROMPT, facts, 1000);
    }

    private BodyGoalDto.Status buildStatus(Long userId, BodyGoal goal) {
        UserBodyRecord latestWeight = bodyRecordRepository.findByUserIdOrderByRecordDate(userId).stream()
                .filter(r -> r.getWeight() != null)
                .reduce((first, second) -> second)
                .orElse(null);
        UserBodyRecord latestInbody = bodyRecordRepository
                .findTopByUserIdAndMeasurementTypeOrderByRecordDateDesc(userId, MeasurementType.INBODY)
                .orElse(null);

        BodyGoalDto.Current current = new BodyGoalDto.Current(
                latestWeight != null ? latestWeight.getRecordDate() : null,
                latestWeight != null ? latestWeight.getWeight() : null,
                latestInbody != null ? latestInbody.getRecordDate() : null,
                latestInbody != null ? latestInbody.getBodyFatMass() : null,
                latestInbody != null ? latestInbody.getSkeletalMuscleMass() : null);

        LocalDate today = LocalDate.now();
        BodyGoalDto.Progress progress = new BodyGoalDto.Progress(
                pct(goal.getStartWeight(), current.weight(), goal.getTargetWeight()),
                pct(goal.getStartFatMass(), current.fatMass(), goal.getTargetFatMass()),
                pct(goal.getStartMuscleMass(), current.muscleMass(), goal.getTargetMuscleMass()),
                ChronoUnit.DAYS.between(goal.getStartDate(), today),
                goal.getTargetDate() != null ? ChronoUnit.DAYS.between(today, goal.getTargetDate()) : null);

        RiderZoneSettingDto zone = riderZoneSettingService.getEffective(userId);
        Integer ftp = zone.getFtp();

        return new BodyGoalDto.Status(goal, current, progress, ftp, zone.isFtpEstimated(),
                wkg(ftp, goal.getStartWeight()), wkg(ftp, current.weight()), wkg(ftp, goal.getTargetWeight()));
    }

    private void validate(BodyGoal g) {
        if (g.getStartDate() == null) throw new IllegalArgumentException("시작일은 필수입니다.");
        if (g.getTargetWeight() == null && g.getTargetFatMass() == null && g.getTargetMuscleMass() == null) {
            throw new IllegalArgumentException("목표 체중·체지방량·골격근량 중 하나는 입력해야 합니다.");
        }
    }

    /** 시작 → 목표 구간에서 현재 위치(%). 감량·증량 모두 같은 식으로 맞는다. */
    private Double pct(Double start, Double current, Double target) {
        if (start == null || current == null || target == null) return null;
        double span = target - start;
        if (Math.abs(span) < 0.05) return null;
        return round((current - start) / span * 100, 0);
    }

    private Double wkg(Integer ftp, Double weight) {
        if (ftp == null || weight == null || weight <= 0) return null;
        return round(ftp / weight, 2);
    }

    private String nv(Object v) {
        return v == null ? "-" : v.toString();
    }

    private double round(double v, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(v * factor) / factor;
    }
}

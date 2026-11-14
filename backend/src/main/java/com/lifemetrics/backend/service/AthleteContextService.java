package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.ExerciseHistoryResponse;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.Bike;
import com.lifemetrics.backend.entity.BikeFit;
import com.lifemetrics.backend.entity.BodyGoal;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.RiderMeasurement;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.health.dto.DailySummary;
import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.entity.HealthTreatmentVisit;
import com.lifemetrics.backend.health.repository.HealthTreatmentRepository;
import com.lifemetrics.backend.health.repository.HealthTreatmentVisitRepository;
import com.lifemetrics.backend.health.service.TreatmentVisitService;
import com.lifemetrics.backend.health.service.DailySummaryService;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.BikeFitRepository;
import com.lifemetrics.backend.repository.BikeRepository;
import com.lifemetrics.backend.repository.BodyGoalRepository;
import com.lifemetrics.backend.repository.RiderMeasurementRepository;
import com.lifemetrics.backend.repository.RiderZoneSettingRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 애슐리트 페르소나 챗에 넘길 기록을 DB 에서 모아 사람이 읽는 텍스트로 만든다.
 * 데이터가 날짜·숫자 표라서 벡터 검색 대신 기간으로 바로 꺼낸다(전부 합쳐 수천 토큰).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AthleteContextService {

    static final int SUMMARY_DAYS = 14;
    static final int RIDE_DAYS = 90;
    static final int FITNESS_DAYS = 30;
    static final int BODY_RECORDS = 8;
    static final int RANDO_MAX = 40;
    static final int MEASUREMENTS = 2;
    static final int CARE_DAYS = 180;
    static final int CARE_MAX = 40;
    static final Set<String> RANDO_TYPES = Set.of("BREVET", "PERMANENT", "FLECHE", "POPULAIRE");

    private final DailySummaryService dailySummaryService;
    private final ActivityCoreRepository activityRepo;
    private final ExerciseLogService exerciseLogService;
    private final UserBodyRecordRepository bodyRepo;
    private final BodyGoalRepository goalRepo;
    private final HealthTreatmentRepository treatmentRepo;
    private final HealthTreatmentVisitRepository visitRepo;
    private final RiderMeasurementRepository measurementRepo;
    private final BikeRepository bikeRepo;
    private final BikeFitRepository fitRepo;
    private final RiderZoneSettingRepository zoneRepo;

    public String build(long userId, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("오늘: ").append(today).append(" (한국 시각)\n\n");
        section(sb, "체성분 (최근 " + BODY_RECORDS + "회 측정)", () -> body(userId));
        section(sb, "진행 중인 체성분 목표", () -> goal(userId));
        section(sb, "파워·심박 기준", () -> zones(userId));
        section(sb, "신체 치수 (피팅용, 최근 " + MEASUREMENTS + "회 측정, 최신순, 길이 mm)", () -> measurements(userId));
        section(sb, "사용 중인 자전거와 최신 피팅 (길이 mm, 각도 °)", this::bikes);
        section(sb, "최근 " + SUMMARY_DAYS + "일 일별 요약 (섭취·소모·에너지 수지)", () -> dailySummaries(userId, today));
        section(sb, "최근 " + RIDE_DAYS + "일 라이딩", () -> rides(userId, today));
        section(sb, "랜도너스 기록 전체 (브레베·퍼머넌트·플레쉬·포퓰레르, 최신순)", () -> randonneuring(userId));
        section(sb, "최근 " + FITNESS_DAYS + "일 웨이트 트레이닝", () -> fitness(userId, today));
        section(sb, "치료·부상 (본인 입력, 진행 중 먼저)", () -> treatments(userId, today));
        section(sb, "진료·처방 내역 (처방전·영수증·세부내역서에서 읽음, 최근 " + CARE_DAYS + "일, 최신순)",
                () -> careVisits(userId, today));
        return sb.toString();
    }

    // ── tool-use 모드에서 쓰는 공개 조회 (ai.tools) ─────────────

    /** tool-use 모드의 시스템 프롬프트용 짧은 요약: 늘 필요한 값만 넣고 나머지는 도구로 조회하게 한다. */
    public String buildBrief(long userId, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("오늘: ").append(today).append(" (한국 시각)\n\n");
        section(sb, "최근 체성분 (1회)", () -> {
            List<String> b = body(userId);
            return b.isEmpty() ? b : List.of(b.get(b.size() - 1));
        });
        section(sb, "진행 중인 체성분 목표", () -> goal(userId));
        section(sb, "파워·심박 기준", () -> zones(userId));
        section(sb, "진행 중인 치료·부상", () -> treatmentRepo.findActiveOn(userId, today).stream()
                .map(t -> treatmentLine(t, today)).toList());
        return sb.toString();
    }

    /** 목표·존·신체 치수·자전거·피팅 (get_profile_and_gear). */
    public String profileAndGear(long userId) {
        StringBuilder sb = new StringBuilder();
        section(sb, "진행 중인 체성분 목표", () -> goal(userId));
        section(sb, "파워·심박 기준", () -> zones(userId));
        section(sb, "신체 치수 (피팅용, 최근 " + MEASUREMENTS + "회 측정, 최신순, 길이 mm)", () -> measurements(userId));
        section(sb, "사용 중인 자전거와 최신 피팅 (길이 mm, 각도 °)", this::bikes);
        return sb.toString();
    }

    /** 기간 안의 체성분 기록, 오래된 순 (get_body_records). type 이 null 이면 전체. */
    public List<String> bodyLines(long userId, LocalDate from, LocalDate to, MeasurementType type) {
        List<UserBodyRecord> all = type == null
                ? bodyRepo.findByUserIdOrderByRecordDate(userId)
                : bodyRepo.findByUserIdAndMeasurementTypeOrderByRecordDate(userId, type);
        return all.stream()
                .filter(r -> !r.getRecordDate().isBefore(from) && !r.getRecordDate().isAfter(to))
                .map(AthleteContextService::bodyLine)
                .toList();
    }

    /** 기간 안의 일별 요약, 오래된 순 (get_daily_summaries). */
    public List<String> dailySummaryLines(long userId, LocalDate from, LocalDate to, LocalDate today) {
        List<String> lines = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            try {
                lines.add(summaryLine(dailySummaryService.summarize(userId, d, today)));
            } catch (Exception e) {
                lines.add(d + ": (집계 실패)");
            }
        }
        return lines;
    }

    /** 기간 안의 웨이트 세션, 날짜순 (get_fitness_sessions). */
    public List<String> fitnessLines(long userId, LocalDate from, LocalDate to) {
        return fitness(userId, from, to);
    }

    /** 치료·부상 목록. activeOnly 면 today 에 진행 중인 것만 (get_treatments). */
    public List<String> treatmentLines(long userId, boolean activeOnly, LocalDate today) {
        List<HealthTreatment> list = activeOnly ? treatmentRepo.findActiveOn(userId, today) : treatmentRepo.findAllForList(userId);
        return list.stream().map(t -> treatmentLine(t, today)).toList();
    }

    /** 기간 안의 진료·처방 내역, 최신순, 최대 limit 건 (get_treatments include_visits). */
    public List<String> visitLines(long userId, LocalDate from, LocalDate to, LocalDate today, int limit) {
        Map<Long, String> names = new java.util.HashMap<>();
        treatmentRepo.findAllForList(userId).forEach(t -> names.put(t.getId(), t.getName()));
        return visitRepo.findByUserIdOrderByVisitDateDescIdDesc(userId).stream()
                .filter(v -> !v.getVisitDate().isBefore(from) && !v.getVisitDate().isAfter(to))
                .limit(limit)
                .map(v -> visitLine(v, names.get(v.getTreatmentId()), today))
                .toList();
    }

    private interface Part { List<String> lines(); }

    /** 한 부분이 실패해도 나머지 기록으로 대화할 수 있게 한다. */
    private void section(StringBuilder sb, String title, Part part) {
        sb.append("## ").append(title).append('\n');
        try {
            List<String> lines = part.lines();
            if (lines.isEmpty()) sb.append("(기록 없음)\n");
            lines.forEach(l -> sb.append("- ").append(l).append('\n'));
        } catch (Exception e) {
            log.warn("애슐리트 컨텍스트 '{}' 수집 실패: {}", title, e.getMessage());
            sb.append("(불러오지 못함)\n");
        }
        sb.append('\n');
    }

    // ── 체성분 ───────────────────────────────────────

    private List<String> body(long userId) {
        List<UserBodyRecord> all = bodyRepo.findByUserIdOrderByRecordDate(userId);
        List<String> lines = new ArrayList<>();
        for (UserBodyRecord r : all.subList(Math.max(0, all.size() - BODY_RECORDS), all.size())) {
            lines.add(bodyLine(r));
        }
        return lines;
    }

    static String bodyLine(UserBodyRecord r) {
        return r.getRecordDate() + " " + r.getMeasurementType()
                + ": 체중 " + num(r.getWeight(), "kg")
                + ", 체지방률 " + num(r.getBodyFatPercentage(), "%")
                + ", 체지방량 " + num(r.getBodyFatMass(), "kg")
                + ", 골격근량 " + num(r.getSkeletalMuscleMass(), "kg");
    }

    private List<String> goal(long userId) {
        return goalRepo.findTopByUserIdAndStatusOrderByIdDesc(userId, BodyGoal.ACTIVE)
                .map(g -> List.of(
                        "시작 " + g.getStartDate() + ": 체중 " + num(g.getStartWeight(), "kg")
                                + ", 체지방량 " + num(g.getStartFatMass(), "kg")
                                + ", 골격근량 " + num(g.getStartMuscleMass(), "kg"),
                        "목표 " + g.getTargetDate() + ": 체중 " + num(g.getTargetWeight(), "kg")
                                + ", 체지방량 " + num(g.getTargetFatMass(), "kg")
                                + ", 골격근량 " + num(g.getTargetMuscleMass(), "kg")
                                + (g.getNotes() == null || g.getNotes().isBlank() ? "" : " (메모: " + g.getNotes() + ")")))
                .orElse(List.of());
    }

    // ── 파워·심박 / 신체 치수 / 자전거·피팅 ───────────────────

    private List<String> zones(long userId) {
        return zoneRepo.findById(userId).map(z -> {
            List<String> l = new ArrayList<>();
            if (z.getFtp() != null) l.add("FTP " + z.getFtp() + "W");
            if (z.getMaxHr() != null) l.add("최대심박 " + z.getMaxHr() + "bpm");
            return l.isEmpty() ? List.<String>of() : List.of(String.join(", ", l));
        }).orElse(List.of());
    }

    private List<String> measurements(long userId) {
        return measurementRepo.findByUserIdOrderByMeasureDateDescIdDesc(userId).stream()
                .limit(MEASUREMENTS).map(AthleteContextService::measurementLine).toList();
    }

    @SuppressWarnings("unchecked")
    static String measurementLine(RiderMeasurement m) {
        List<String> p = new ArrayList<>();
        add(p, "키", m.getHeightCm(), "cm");
        add(p, "인심", m.getInseamMm(), "");
        add(p, "상체", m.getTorsoMm(), "");
        add(p, "팔", m.getArmMm(), "");
        add(p, "어깨너비", m.getShoulderWidthMm(), "");
        add(p, "좌골너비", m.getSitBoneWidthMm(), "");
        lr(p, "발길이", m.getFootLengthLeftMm(), m.getFootLengthRightMm());
        lr(p, "발볼너비", m.getFootWidthLeftMm(), m.getFootWidthRightMm());
        if (m.getArchType() != null) p.add("아치 " + m.getArchType());
        add(p, "신발 사이즈", m.getShoeSizeMm(), "");
        add(p, "신발 EU", m.getShoeSizeEu(), "");
        if (m.getShoeModel() != null) p.add("신발 " + m.getShoeModel());
        if (m.getInsole() != null) p.add("인솔 " + m.getInsole());
        if (m.getFlexibility() != null) p.add("유연성 " + m.getFlexibility());
        StringBuilder l = new StringBuilder(String.valueOf(m.getMeasureDate()));
        if (m.getMeasuredBy() != null) l.append(" (").append(m.getMeasuredBy()).append(')');
        l.append(": ").append(p.isEmpty() ? "핵심 치수 없음" : String.join(", ", p));
        Object items = m.getDetailJson() == null ? null : m.getDetailJson().get("items");
        if (items instanceof List<?> list && !list.isEmpty()) {
            Object unit = m.getDetailJson().get("unit");
            List<String> d = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> it) || it.get("item") == null) continue;
                if (it.containsKey("value")) d.add(it.get("item") + " " + fmt(it.get("value")));
                else d.add(it.get("item") + " L" + fmt(it.get("left")) + "/R" + fmt(it.get("right")));
            }
            l.append(" / 상세(").append(unit == null ? "mm" : unit).append("): ").append(String.join(", ", d));
        }
        if (m.getNotes() != null) l.append(" / 메모: ").append(m.getNotes());
        return l.toString();
    }

    private List<String> bikes() {
        List<String> lines = new ArrayList<>();
        for (Bike b : bikeRepo.findByIsRetiredFalseOrderByIsDefaultDescIdDesc()) {
            lines.add(bikeLine(b));
            fitRepo.findByBikeIdOrderByFitDateDescIdDesc(b.getId()).stream().findFirst()
                    .ifPresent(f -> lines.add("  └ " + fitLine(f)));
        }
        return lines;
    }

    static String bikeLine(Bike b) {
        StringBuilder l = new StringBuilder(b.getName());
        if (Boolean.TRUE.equals(b.getIsDefault())) l.append(" (주력)");
        List<String> p = new ArrayList<>();
        if (b.getBikeType() != null) p.add(b.getBikeType());
        if (b.getFrameSize() != null) p.add("프레임 " + b.getFrameSize());
        if (b.getFrameMaterial() != null) p.add(b.getFrameMaterial());
        if (b.getWeightKg() != null) p.add(num(b.getWeightKg(), "kg"));
        if (b.getGroupset() != null) p.add(b.getGroupset());
        if (b.getChainringOuter() != null && b.getChainringInner() != null) {
            p.add(b.getChainringOuter() + "/" + b.getChainringInner() + "T" + (b.getCassetteLabel() == null ? "" : " × " + b.getCassetteLabel()));
        }
        if (b.getTotalDistance() != null && b.getTotalDistance() > 0) {
            p.add(String.format(Locale.ROOT, "누적 %,.0fkm", b.getTotalDistance() / 1000));
        }
        if (!p.isEmpty()) l.append(": ").append(String.join(", ", p));
        return l.toString();
    }

    static String fitLine(BikeFit f) {
        List<String> p = new ArrayList<>();
        add(p, "안장높이", f.getSaddleHeightMm(), "");
        add(p, "셋백", f.getSaddleSetbackMm(), "");
        add(p, "안장각", f.getSaddleTiltDeg(), "°");
        if (f.getSaddleModel() != null) p.add("안장 " + f.getSaddleModel());
        add(p, "리치", f.getReachMm(), "");
        add(p, "드롭", f.getDropMm(), "");
        add(p, "스템", f.getStemLengthMm(), "");
        add(p, "스템각", f.getStemAngleDeg(), "°");
        add(p, "스페이서", f.getSpacerMm(), "");
        add(p, "핸들바", f.getHandlebarWidthMm(), "");
        add(p, "크랭크", f.getCrankLengthMm(), "");
        if (f.getCleatLeft() != null) p.add("클릿 L " + f.getCleatLeft());
        if (f.getCleatRight() != null) p.add("클릿 R " + f.getCleatRight());
        if (f.getExtraJson() != null) f.getExtraJson().forEach((k, v) -> { if (v != null) p.add(k + " " + fmt(v)); });
        StringBuilder l = new StringBuilder("피팅 ").append(f.getFitDate());
        if (f.getFitter() != null) l.append(" (").append(f.getFitter()).append(')');
        l.append(": ").append(p.isEmpty() ? "수치 없음" : String.join(", ", p));
        if (f.getNotes() != null) l.append(" / 메모: ").append(f.getNotes());
        return l.toString();
    }

    private static void add(List<String> p, String label, Double v, String unit) {
        if (v != null) p.add(label + " " + num(v, unit));
    }

    private static void lr(List<String> p, String label, Double left, Double right) {
        if (left != null || right != null) p.add(label + " L" + num(left, "") + "/R" + num(right, ""));
    }

    private static String fmt(Object o) {
        if (o == null) return "-";
        if (o instanceof Number n) return num(n.doubleValue(), "");
        return String.valueOf(o);
    }

    // ── 일별 요약 ─────────────────────────────────────

    private List<String> dailySummaries(long userId, LocalDate today) {
        List<String> lines = new ArrayList<>();
        for (int i = SUMMARY_DAYS - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            try {
                lines.add(summaryLine(dailySummaryService.summarize(userId, d, today)));
            } catch (Exception e) {
                lines.add(d + ": (집계 실패)");
            }
        }
        return lines;
    }

    static String summaryLine(DailySummary s) {
        StringBuilder l = new StringBuilder(s.date().toString());
        if (!s.dayFinished()) l.append("(오늘, 진행 중)");
        l.append(": ");
        DailySummary.Meals m = s.meals();
        if (m == null || m.recordedMeals() == null || m.recordedMeals().isEmpty()) {
            l.append("식단 기록 없음");
        } else {
            l.append("섭취 ").append(m.calories()).append("kcal (탄").append(Math.round(m.carbsG()))
                    .append("/단").append(Math.round(m.proteinG())).append("/지").append(Math.round(m.fatG())).append("g, ")
                    .append("기록 끼니 ").append(String.join("·", m.recordedMeals()));
            if (m.missingMeals() != null && !m.missingMeals().isEmpty()) {
                l.append(", 빠진 끼니 ").append(String.join("·", m.missingMeals()));
            }
            l.append(')');
        }
        DailySummary.Energy e = s.energy();
        if (e != null) {
            if (e.burnedKcal() != null) l.append(", 소모 ").append(e.burnedKcal()).append("kcal");
            if (e.balanceKcal() != null) l.append(", 수지 ").append(signed(e.balanceKcal())).append("kcal");
        }
        if (s.activity() != null && s.activity().steps() != null) l.append(", 걸음 ").append(s.activity().steps());
        if (s.rides() != null && !s.rides().isEmpty()) {
            double km = s.rides().stream().mapToDouble(r -> r.distanceM() == null ? 0 : r.distanceM()).sum() / 1000;
            l.append(", 라이딩 ").append(String.format(Locale.ROOT, "%.1f", km)).append("km");
        }
        if (s.fitness() != null && !s.fitness().isEmpty()) {
            l.append(", 웨이트 ").append(s.fitness().size()).append("회");
        }
        return l.toString();
    }

    // ── 라이딩 ───────────────────────────────────────

    private List<String> rides(long userId, LocalDate today) {
        return activityRepo.findByUserIdAndDateRange(userId,
                        today.minusDays(RIDE_DAYS).atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream().map(AthleteContextService::rideLine).toList();
    }

    private List<String> randonneuring(long userId) {
        return activityRepo.findByUserIdOrderByStartTimeDesc(userId).stream()
                .filter(a -> a.getRideType() != null && RANDO_TYPES.contains(a.getRideType().toUpperCase(Locale.ROOT)))
                .limit(RANDO_MAX)
                .map(AthleteContextService::rideLine)
                .toList();
    }

    public static String rideLine(ActivityCore a) {
        LocalDateTime t = a.getStartTime();
        StringBuilder l = new StringBuilder(t == null ? "날짜 없음" : t.toLocalDate().toString());
        if (a.getRideType() != null) l.append(" [").append(a.getRideType()).append(']');
        if (a.getName() != null) l.append(' ').append(a.getName());
        l.append(": ");
        l.append(a.getTotalDistance() == null ? "거리 -" : String.format(Locale.ROOT, "%.1fkm", a.getTotalDistance() / 1000));
        if (a.getMovingTime() != null) l.append(", 이동 ").append(hm(a.getMovingTime()));
        if (a.getTotalAscent() != null) l.append(", 상승 ").append(Math.round(a.getTotalAscent())).append('m');
        if (a.getAvgHeartRate() != null) l.append(", 평균심박 ").append(Math.round(a.getAvgHeartRate()));
        if (a.getAvgPower() != null) l.append(", 평균파워 ").append(Math.round(a.getAvgPower())).append('W');
        if (a.getCalories() > 0) l.append(", ").append(a.getCalories()).append("kcal");
        return l.toString();
    }

    // ── 웨이트 ───────────────────────────────────────

    private List<String> fitness(long userId, LocalDate today) {
        return fitness(userId, today.minusDays(FITNESS_DAYS), today);
    }

    private List<String> fitness(long userId, LocalDate from, LocalDate today) {
        List<String> lines = new ArrayList<>();
        for (YearMonth ym = YearMonth.from(from); !ym.isAfter(YearMonth.from(today)); ym = ym.plusMonths(1)) {
            for (ExerciseHistoryResponse s : exerciseLogService.getHistory(userId, ym.toString())) {
                LocalDate d = LocalDate.parse(s.getSessionDate());
                if (d.isBefore(from) || d.isAfter(today)) continue;
                List<String> names = new ArrayList<>();
                for (ExerciseHistoryResponse.ExerciseLogDto log : s.getLogs()) names.add(log.getExerciseNameKo());
                lines.add(d + (Boolean.TRUE.equals(s.getIsPT()) ? " (PT)" : "")
                        + ": " + String.join(", ", names)
                        + " / " + (s.getTotalSets() == null ? "-" : s.getTotalSets()) + "세트"
                        + ", 볼륨 " + num(s.getTotalVolume(), "kg"));
            }
        }
        lines.sort(null);
        return lines;
    }

    // ── 치료·진료 ─────────────────────────────────────

    private List<String> treatments(long userId, LocalDate today) {
        return treatmentRepo.findAllForList(userId).stream().map(t -> treatmentLine(t, today)).toList();
    }

    static String treatmentLine(HealthTreatment t, LocalDate today) {
        StringBuilder l = new StringBuilder(t.getName());
        if (t.getDiagnosisCode() != null) l.append(" [").append(t.getDiagnosisCode()).append(']');
        if (t.getBodyPart() != null) l.append(" (").append(t.getBodyPart()).append(')');
        l.append(": ").append(t.getStartDate()).append(" 시작");
        if (t.getEndDate() == null) {
            l.append(", 치료 중 ").append(today.toEpochDay() - t.getStartDate().toEpochDay()).append("일째");
        } else {
            l.append(" ~ ").append(t.getEndDate()).append(" 종료");
        }
        if (t.getRestrictions() != null) l.append(" / 운동 제한·권고: ").append(t.getRestrictions());
        if (t.getNotes() != null) l.append(" / 메모: ").append(t.getNotes());
        return l.toString();
    }

    private List<String> careVisits(long userId, LocalDate today) {
        return visitLines(userId, today.minusDays(CARE_DAYS), today, today, CARE_MAX);
    }

    /** "2026-11-05 정형외과의원 정형외과 [진료비 세부내역서] (장경인대 증후군 치료): 주사: …; 약: … — 오늘 복용 기간 중 / 본인부담 12,300원" */
    static String visitLine(HealthTreatmentVisit v, String treatmentName, LocalDate today) {
        long daysAgo = today.toEpochDay() - v.getVisitDate().toEpochDay();
        StringBuilder l = new StringBuilder(v.getVisitDate().toString());
        if (v.getInstitution() != null) l.append(' ').append(v.getInstitution());
        if (v.getDepartment() != null) l.append(' ').append(v.getDepartment());
        if (v.getDocType() != null) l.append(" [").append(v.getDocType()).append(']');
        if (treatmentName != null) l.append(" (").append(treatmentName).append(" 치료)");
        l.append(": ");
        List<String> items = new ArrayList<>();
        for (Map<String, Object> it : v.getItems() == null ? List.<Map<String, Object>>of() : v.getItems()) {
            if ("진찰".equals(it.get("category")) || "재료".equals(it.get("category"))) continue;
            boolean ongoing = "약".equals(it.get("category"))
                    && it.get("days") instanceof Number n && daysAgo < n.intValue();
            items.add(TreatmentVisitService.itemLine(it) + (ongoing ? " — 오늘 복용 기간 중" : ""));
        }
        if (!items.isEmpty()) l.append(String.join("; ", items));
        else l.append(v.getSummary() != null ? v.getSummary() : "진찰만");
        if (v.getDiagnosis() != null) l.append(" / 상병 ").append(v.getDiagnosis());
        if (v.getPatientAmount() != null) l.append(String.format(Locale.ROOT, " / 본인부담 %,d원", v.getPatientAmount()));
        return l.toString();
    }

    // ── 형식 ─────────────────────────────────────────

    public static String num(Double v, String unit) {
        if (v == null) return "-";
        String s = v == Math.rint(v) ? String.valueOf(v.longValue()) : String.format(Locale.ROOT, "%.1f", v);
        return s + unit;
    }

    public static String hm(int seconds) {
        return (seconds / 3600) + "시간 " + (seconds % 3600 / 60) + "분";
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }
}

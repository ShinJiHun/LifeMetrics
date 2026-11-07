package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.ExerciseHistoryResponse;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.BodyGoal;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.health.dto.DailySummary;
import com.lifemetrics.backend.health.service.DailySummaryService;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.BodyGoalRepository;
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
    static final Set<String> RANDO_TYPES = Set.of("BREVET", "PERMANENT", "FLECHE", "POPULAIRE");

    private final DailySummaryService dailySummaryService;
    private final ActivityCoreRepository activityRepo;
    private final ExerciseLogService exerciseLogService;
    private final UserBodyRecordRepository bodyRepo;
    private final BodyGoalRepository goalRepo;

    public String build(long userId, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("오늘: ").append(today).append(" (한국 시각)\n\n");
        section(sb, "체성분 (최근 " + BODY_RECORDS + "회 측정)", () -> body(userId));
        section(sb, "진행 중인 체성분 목표", () -> goal(userId));
        section(sb, "최근 " + SUMMARY_DAYS + "일 일별 요약 (섭취·소모·에너지 수지)", () -> dailySummaries(userId, today));
        section(sb, "최근 " + RIDE_DAYS + "일 라이딩", () -> rides(userId, today));
        section(sb, "랜도너스 기록 전체 (브레베·퍼머넌트·플레쉬·포퓰레르, 최신순)", () -> randonneuring(userId));
        section(sb, "최근 " + FITNESS_DAYS + "일 웨이트 트레이닝", () -> fitness(userId, today));
        return sb.toString();
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
            lines.add(r.getRecordDate() + " " + r.getMeasurementType()
                    + ": 체중 " + num(r.getWeight(), "kg")
                    + ", 체지방률 " + num(r.getBodyFatPercentage(), "%")
                    + ", 체지방량 " + num(r.getBodyFatMass(), "kg")
                    + ", 골격근량 " + num(r.getSkeletalMuscleMass(), "kg"));
        }
        return lines;
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

    static String rideLine(ActivityCore a) {
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
        LocalDate from = today.minusDays(FITNESS_DAYS);
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

    // ── 형식 ─────────────────────────────────────────

    static String num(Double v, String unit) {
        if (v == null) return "-";
        String s = v == Math.rint(v) ? String.valueOf(v.longValue()) : String.format(Locale.ROOT, "%.1f", v);
        return s + unit;
    }

    static String hm(int seconds) {
        return (seconds / 3600) + "시간 " + (seconds % 3600 / 60) + "분";
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }
}

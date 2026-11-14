package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.lifemetrics.backend.service.AthleteContextService.hm;

/** 기간을 주·월·전체로 묶은 라이딩 합계. 긴 기간 추세·비교는 개별 기록 대신 이 도구로. */
@Component
@RequiredArgsConstructor
public class GetRideStatsTool implements AthleteTool {

    private final ActivityCoreRepository activityRepo;

    @Override
    public String name() {
        return "get_ride_stats";
    }

    @Override
    public String description() {
        return "기간 안의 라이딩을 묶어 합계를 낸다: 횟수, 거리, 이동시간, 상승, kcal, 평균파워(이동시간 가중, 파워 있는 기록만), "
                + "평균심박(이동시간 가중), 최장 거리. group_by 는 week(월요일 시작)·month·total. "
                + "몇 달 이상의 추세나 두 기간 비교는 get_activities 대신 이 도구를 써라.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("from", ToolInputs.dateProp("시작일 (포함)"));
        p.put("to", ToolInputs.dateProp("종료일 (포함). 없으면 오늘"));
        p.put("group_by", Map.of("type", "string", "enum", List.of("week", "month", "total"),
                "description", "묶는 단위 (기본 month)"));
        p.put("ride_type", Map.of("type", "string", "description", "라이딩 유형 필터 (예: BREVET). 없으면 전체"));
        return ToolInputs.schema(p, List.of("from"));
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        LocalDate from = ToolInputs.date(input, "from", null);
        LocalDate to = ToolInputs.date(input, "to", today);
        ToolInputs.requireOrder(from, to);
        String groupBy = ToolInputs.text(input, "group_by");
        groupBy = groupBy == null ? "month" : groupBy.toLowerCase(Locale.ROOT);
        if (!List.of("week", "month", "total").contains(groupBy)) {
            throw new IllegalArgumentException("group_by 는 week·month·total 중 하나입니다: '" + groupBy + "'");
        }
        String type = ToolInputs.text(input, "ride_type");

        Map<String, Agg> groups = new TreeMap<>();
        for (ActivityCore a : activityRepo.findByUserIdAndDateRange(userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            if (a.getStartTime() == null) continue;
            if (type != null && (a.getRideType() == null || !a.getRideType().equalsIgnoreCase(type))) continue;
            groups.computeIfAbsent(key(a.getStartTime().toLocalDate(), groupBy), k -> new Agg()).add(a);
        }
        List<String> lines = new ArrayList<>();
        groups.forEach((k, g) -> lines.add(k + ": " + g));
        String header = "라이딩 통계 " + from + " ~ " + to + " (" + groupBy + (type == null ? "" : ", " + type) + ")";
        return ToolInputs.lines(header, lines, "group_by 를 month·total 로 키워라");
    }

    static String key(LocalDate d, String groupBy) {
        return switch (groupBy) {
            case "week" -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) + " 주";
            case "month" -> YearMonth.from(d).toString();
            default -> "전체";
        };
    }

    static final class Agg {
        int count;
        double km, ascent, longestKm;
        long movingSec, kcal, powerSec, hrSec;
        double powerWeighted, hrWeighted;

        void add(ActivityCore a) {
            count++;
            double dist = a.getTotalDistance() == null ? 0 : a.getTotalDistance() / 1000;
            km += dist;
            longestKm = Math.max(longestKm, dist);
            if (a.getTotalAscent() != null) ascent += a.getTotalAscent();
            int sec = a.getMovingTime() == null ? 0 : a.getMovingTime();
            movingSec += sec;
            kcal += a.getCalories();
            if (a.getAvgPower() != null && a.getAvgPower() > 0 && sec > 0) {
                powerWeighted += a.getAvgPower() * sec;
                powerSec += sec;
            }
            if (a.getAvgHeartRate() != null && a.getAvgHeartRate() > 0 && sec > 0) {
                hrWeighted += a.getAvgHeartRate() * sec;
                hrSec += sec;
            }
        }

        @Override
        public String toString() {
            StringBuilder l = new StringBuilder(String.format(Locale.ROOT, "%d회, %.1fkm, 이동 %s, 상승 %,.0fm",
                    count, km, hm((int) movingSec), ascent));
            if (kcal > 0) l.append(String.format(Locale.ROOT, ", %,dkcal", kcal));
            if (powerSec > 0) l.append(", 평균파워 ").append(Math.round(powerWeighted / powerSec)).append('W');
            if (hrSec > 0) l.append(", 평균심박 ").append(Math.round(hrWeighted / hrSec));
            l.append(String.format(Locale.ROOT, ", 최장 %.1fkm", longestKm));
            return l.toString();
        }
    }
}

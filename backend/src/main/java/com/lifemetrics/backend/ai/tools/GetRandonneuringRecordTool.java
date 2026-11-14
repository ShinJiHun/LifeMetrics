package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.RandonneuringResult;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.RandonneuringResultRepository;
import com.lifemetrics.backend.service.RandonneuringCareer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lifemetrics.backend.service.AthleteContextService.hm;

/** 랜도너스 공식 참가 기록(완주/DNF). GPS 가 없는 대회도 포함된 경력의 기준. */
@Component
@RequiredArgsConstructor
public class GetRandonneuringRecordTool implements AthleteTool {

    private final RandonneuringResultRepository repo;
    private final ActivityCoreRepository activityRepo;

    @Override
    public String name() {
        return "get_randonneuring_record";
    }

    @Override
    public String description() {
        return "랜도너스 공식 참가 기록(브레베·퍼머넌트·플레쉬)을 최신순으로 조회한다: 날짜, 유형, 코스명, 코스 거리, 완주/DNF, "
                + "연결된 라이딩 기록 id, 메모. 맨 위에 전체 경력 요약(참가·완주·DNF 수, 완주 거리, 최장 완주, 연도별)을 붙인다. "
                + "GPS 기록이 없는 대회도 있으므로 경력·완주 이력·DNF 패턴 질문은 get_activities 가 아니라 이 도구를 써라. "
                + "GPS 라이딩 기록이 연결된 대회는 'GPS:' 뒤에 실제 주행 거리·이동 시간·이동 평속·평균 심박·평균 파워·상승을 붙인다. "
                + "공식 평균은 완주 시간(휴식·대기 포함) 기준이고 GPS 평속은 움직인 시간 기준이라 둘의 차이가 쉰 시간이다. "
                + "더 자세한 주행 데이터(최대값·구간·날씨)는 (활동 id) 로 get_activity_detail 을 불러라.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("year", Map.of("type", "integer", "description", "연도. 없으면 전체"));
        p.put("event_type", Map.of("type", "string", "enum", List.of("BREVET", "PERMANENT", "FLECHE", "POPULAIRE"),
                "description", "유형. 없으면 전체"));
        p.put("status", Map.of("type", "string", "enum", List.of("FINISHED", "DNF"), "description", "없으면 전체"));
        return ToolInputs.schema(p, List.of());
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        Integer year = input != null && input.path("year").canConvertToInt() ? input.path("year").asInt() : null;
        String type = upper(ToolInputs.text(input, "event_type"));
        String status = upper(ToolInputs.text(input, "status"));
        if (status != null && !status.equals(RandonneuringResult.FINISHED) && !status.equals(RandonneuringResult.DNF)) {
            throw new IllegalArgumentException("status 는 FINISHED 또는 DNF 입니다: '" + status + "'");
        }

        List<RandonneuringResult> all = repo.findByUserIdOrderByEventDateDescIdDesc(userId);
        List<RandonneuringResult> picked = all.stream()
                .filter(r -> year == null || r.getEventDate().getYear() == year)
                .filter(r -> type == null || type.equals(r.getEventType()))
                .filter(r -> status == null || status.equals(r.getStatus()))
                .toList();

        String career = RandonneuringCareer.summary(all);
        String filter = (year == null ? "" : year + "년 ") + (type == null ? "" : type + " ") + (status == null ? "" : status + " ");
        String header = "전체 경력: " + (career == null ? "(기록 없음)" : career)
                + "\n조회 " + (filter.isBlank() ? "전체" : filter.trim()) + ": " + picked.size() + "건 (최신순)";
        Map<Long, ActivityCore> rides = activityRepo.findAllById(picked.stream()
                        .map(RandonneuringResult::getActivityCoreId).filter(Objects::nonNull).distinct().toList())
                .stream().filter(a -> Objects.equals(a.getUserId(), userId))
                .collect(Collectors.toMap(ActivityCore::getId, Function.identity()));
        List<String> lines = picked.stream().map(r -> {
            ActivityCore a = r.getActivityCoreId() == null ? null : rides.get(r.getActivityCoreId());
            return RandonneuringCareer.line(r) + (a == null ? "" : " / GPS: " + gps(a));
        }).toList();
        return ToolInputs.lines(header, lines, "year 로 좁혀라");
    }

    /** "403.2km, 이동 19시간 40분, 이동 평속 20.5km/h, 심박 132, 파워 145W, 상승 3,120m" */
    static String gps(ActivityCore a) {
        StringBuilder l = new StringBuilder(a.getTotalDistance() == null ? "거리 -"
                : String.format(Locale.ROOT, "%.1fkm", a.getTotalDistance() / 1000));
        if (a.getMovingTime() != null) l.append(", 이동 ").append(hm(a.getMovingTime()));
        if (a.getAvgSpeed() != null) l.append(String.format(Locale.ROOT, ", 이동 평속 %.1fkm/h", a.getAvgSpeed()));
        if (a.getAvgHeartRate() != null) l.append(", 심박 ").append(Math.round(a.getAvgHeartRate()));
        if (a.getAvgPower() != null && a.getAvgPower() > 0) l.append(", 파워 ").append(Math.round(a.getAvgPower())).append('W');
        if (a.getTotalAscent() != null) l.append(String.format(Locale.ROOT, ", 상승 %,.0fm", a.getTotalAscent()));
        return l.toString();
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}

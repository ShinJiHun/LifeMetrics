package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.service.AthleteContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetActivitiesTool implements AthleteTool {

    static final int DEFAULT_LIMIT = 30;
    static final int MAX_LIMIT = 50;

    private final ActivityCoreRepository activityRepo;

    @Override
    public String name() {
        return "get_activities";
    }

    @Override
    public String description() {
        return "기간 안의 라이딩 기록을 최신순으로 조회한다. 한 줄에 한 건: 날짜 [유형] 이름: 거리, 이동시간, 상승, "
                + "평균심박, 평균파워, kcal. ride_type 으로 BREVET·PERMANENT·FLECHE·POPULAIRE 등 유형만 걸러낼 수 있다. "
                + "기간 합계·추세가 필요하면 기록을 직접 더하지 말고 범위를 나눠 여러 번 조회하라.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("from", ToolInputs.dateProp("시작일 (포함)"));
        p.put("to", ToolInputs.dateProp("종료일 (포함). 없으면 오늘"));
        p.put("ride_type", Map.of("type", "string", "description", "라이딩 유형 필터 (예: BREVET). 없으면 전체"));
        p.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT,
                "description", "최대 건수 (기본 " + DEFAULT_LIMIT + ")"));
        return ToolInputs.schema(p, List.of("from"));
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        LocalDate from = ToolInputs.date(input, "from", null);
        LocalDate to = ToolInputs.date(input, "to", today);
        ToolInputs.requireOrder(from, to);
        String type = ToolInputs.text(input, "ride_type");
        int limit = ToolInputs.intIn(input, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);

        List<ActivityCore> rides = activityRepo.findByUserIdAndDateRange(
                userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<ActivityCore> filtered = rides.stream()
                .filter(a -> type == null || (a.getRideType() != null
                        && a.getRideType().toUpperCase(Locale.ROOT).equals(type.toUpperCase(Locale.ROOT))))
                .toList();
        List<String> lines = filtered.stream().limit(limit).map(AthleteContextService::rideLine).toList();

        String header = "라이딩 " + from + " ~ " + to + (type == null ? "" : " [" + type + "]")
                + ": 전체 " + filtered.size() + "건" + (filtered.size() > limit ? ", 최신 " + limit + "건 표시" : "");
        return ToolInputs.lines(header, lines, "기간을 좁혀 다시 조회하라");
    }
}

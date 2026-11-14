package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.service.AthleteContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetFitnessSessionsTool implements AthleteTool {

    static final int MAX_DAYS = 186;

    private final AthleteContextService contextService;

    @Override
    public String name() {
        return "get_fitness_sessions";
    }

    @Override
    public String description() {
        return "기간 안의 웨이트 트레이닝 세션을 날짜순으로 조회한다: 날짜(PT 여부), 운동 종목, 총 세트 수, 볼륨(kg). "
                + "한 번에 최대 " + MAX_DAYS + "일.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("from", ToolInputs.dateProp("시작일 (포함)"));
        p.put("to", ToolInputs.dateProp("종료일 (포함). 없으면 오늘"));
        return ToolInputs.schema(p, List.of("from"));
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        LocalDate from = ToolInputs.date(input, "from", null);
        LocalDate to = ToolInputs.date(input, "to", today);
        ToolInputs.requireOrder(from, to);
        if (to.isAfter(today)) to = today;
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new IllegalArgumentException("한 번에 최대 " + MAX_DAYS + "일까지 조회합니다 (요청 " + days + "일). 기간을 나눠 호출하라.");
        }
        List<String> lines = contextService.fitnessLines(userId, from, to);
        return ToolInputs.lines("웨이트 " + from + " ~ " + to + ": " + lines.size() + "회", lines, "기간을 나눠 호출하라");
    }
}

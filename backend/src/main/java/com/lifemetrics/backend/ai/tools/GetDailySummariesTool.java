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
public class GetDailySummariesTool implements AthleteTool {

    static final int MAX_DAYS = 31;

    private final AthleteContextService contextService;

    @Override
    public String name() {
        return "get_daily_summaries";
    }

    @Override
    public String description() {
        return "하루 단위 요약(섭취 kcal·탄단지, 기록된/빠진 끼니, 소모 kcal, 에너지 수지, 걸음, 라이딩 km, 웨이트 횟수)을 "
                + "날짜순으로 조회한다. 한 번에 최대 " + MAX_DAYS + "일. 빠진 끼니가 있는 날의 섭취·수지는 실제 하루 값이 아니다.";
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
        List<String> lines = contextService.dailySummaryLines(userId, from, to, today);
        return ToolInputs.lines("일별 요약 " + from + " ~ " + to + " (" + days + "일)", lines, "기간을 나눠 호출하라");
    }
}

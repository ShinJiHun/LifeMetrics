package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.MeasurementType;
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
public class GetBodyRecordsTool implements AthleteTool {

    private final AthleteContextService contextService;

    @Override
    public String name() {
        return "get_body_records";
    }

    @Override
    public String description() {
        return "기간 안의 체성분 측정 기록(체중, 체지방률, 체지방량, 골격근량)을 오래된 순으로 조회한다. "
                + "type 은 INBODY(인바디 기기) 또는 FITDAYS(체중계). 둘은 측정 방식이 달라 직접 비교할 때 주의하라.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("from", ToolInputs.dateProp("시작일 (포함). 없으면 1년 전"));
        p.put("to", ToolInputs.dateProp("종료일 (포함). 없으면 오늘"));
        p.put("type", Map.of("type", "string", "enum", List.of("INBODY", "FITDAYS"),
                "description", "측정 종류. 없으면 전체"));
        return ToolInputs.schema(p, List.of());
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        LocalDate from = ToolInputs.date(input, "from", today.minusYears(1));
        LocalDate to = ToolInputs.date(input, "to", today);
        ToolInputs.requireOrder(from, to);
        String t = ToolInputs.text(input, "type");
        MeasurementType type;
        try {
            type = t == null ? null : MeasurementType.valueOf(t.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("type 은 INBODY 또는 FITDAYS 입니다: '" + t + "'");
        }
        List<String> lines = contextService.bodyLines(userId, from, to, type);
        String header = "체성분 " + from + " ~ " + to + (type == null ? "" : " [" + type + "]") + ": " + lines.size() + "회";
        return ToolInputs.lines(header, lines, "기간을 좁혀 다시 조회하라");
    }
}

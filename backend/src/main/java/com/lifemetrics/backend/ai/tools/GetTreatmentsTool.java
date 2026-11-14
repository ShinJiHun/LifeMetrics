package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
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
public class GetTreatmentsTool implements AthleteTool {

    static final int MAX_VISITS = 40;

    private final AthleteContextService contextService;

    @Override
    public String name() {
        return "get_treatments";
    }

    @Override
    public String description() {
        return "치료·부상 기록(부위, 진단코드, 시작·종료일, 운동 제한·권고, 메모)을 조회한다. status=active 면 오늘 진행 중인 것만, all 이면 전체. "
                + "include_visits=true 면 기간 안의 진료·처방 내역(기관, 시술, 약과 복용 기간 중 여부, 상병, 본인부담)도 최신순으로 붙인다.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("status", Map.of("type", "string", "enum", List.of("active", "all"), "description", "기본 all"));
        p.put("include_visits", Map.of("type", "boolean", "description", "진료·처방 내역 포함 (기본 false)"));
        p.put("from", ToolInputs.dateProp("진료 내역 시작일. 없으면 180일 전"));
        p.put("to", ToolInputs.dateProp("진료 내역 종료일. 없으면 오늘"));
        return ToolInputs.schema(p, List.of());
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        String status = ToolInputs.text(input, "status");
        status = status == null ? "all" : status.toLowerCase(Locale.ROOT);
        if (!status.equals("active") && !status.equals("all")) {
            throw new IllegalArgumentException("status 는 active 또는 all 입니다: '" + status + "'");
        }
        StringBuilder sb = new StringBuilder(ToolInputs.lines(
                "치료·부상 (" + status + ")", contextService.treatmentLines(userId, status.equals("active"), today), ""));

        boolean visits = input != null && input.path("include_visits").asBoolean(false);
        if (visits) {
            LocalDate from = ToolInputs.date(input, "from", today.minusDays(180));
            LocalDate to = ToolInputs.date(input, "to", today);
            ToolInputs.requireOrder(from, to);
            sb.append("\n\n").append(ToolInputs.lines("진료·처방 " + from + " ~ " + to + " (최신순, 최대 " + MAX_VISITS + "건)",
                    contextService.visitLines(userId, from, to, today, MAX_VISITS), "기간을 좁혀라"));
        }
        String out = sb.toString();
        return out.length() <= MAX_RESULT_CHARS ? out : out.substring(0, MAX_RESULT_CHARS) + "\n(길어서 잘림. 기간을 좁혀라)";
    }
}

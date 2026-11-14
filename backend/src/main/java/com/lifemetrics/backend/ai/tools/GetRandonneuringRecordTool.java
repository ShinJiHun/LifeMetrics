package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.RandonneuringResult;
import com.lifemetrics.backend.repository.RandonneuringResultRepository;
import com.lifemetrics.backend.service.RandonneuringCareer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 랜도너스 공식 참가 기록(완주/DNF). GPS 가 없는 대회도 포함된 경력의 기준. */
@Component
@RequiredArgsConstructor
public class GetRandonneuringRecordTool implements AthleteTool {

    private final RandonneuringResultRepository repo;

    @Override
    public String name() {
        return "get_randonneuring_record";
    }

    @Override
    public String description() {
        return "랜도너스 공식 참가 기록(브레베·퍼머넌트·플레쉬)을 최신순으로 조회한다: 날짜, 유형, 코스명, 코스 거리, 완주/DNF, "
                + "연결된 라이딩 기록 id, 메모. 맨 위에 전체 경력 요약(참가·완주·DNF 수, 완주 거리, 최장 완주, 연도별)을 붙인다. "
                + "GPS 기록이 없는 대회도 있으므로 경력·완주 이력·DNF 패턴 질문은 get_activities 가 아니라 이 도구를 써라. "
                + "상세 주행 데이터는 (활동 id) 로 get_activity_detail 을 불러라.";
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
        return ToolInputs.lines(header, picked.stream().map(RandonneuringCareer::line).toList(), "year 로 좁혀라");
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}

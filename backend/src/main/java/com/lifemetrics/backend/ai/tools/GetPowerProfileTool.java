package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.power.PowerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 파워 프로필(파워 커브·라이더 성향·피로 저항)을 챗 tools 모드에서도 쓰게 한다. */
@Component
@RequiredArgsConstructor
public class GetPowerProfileTool implements AthleteTool {

    private final PowerProfileService powerProfileService;

    @Override
    public String name() {
        return "get_power_profile";
    }

    @Override
    public String description() {
        return "파워미터 기록 전체로 계산한 파워 프로필을 조회한다: 구간별 최고 평균 파워(파워 커브), 라이더 성향, 피로 저항. "
                + "강점·약점, 훈련 방향, 페이싱 질문에 쓴다.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolInputs.schema(Map.of(), List.of());
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        return ToolInputs.lines("파워 프로필", powerProfileService.summaryLines(userId), "");
    }
}

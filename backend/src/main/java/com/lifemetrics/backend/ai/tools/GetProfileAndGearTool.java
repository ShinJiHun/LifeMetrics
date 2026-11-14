package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.service.AthleteContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetProfileAndGearTool implements AthleteTool {

    private final AthleteContextService contextService;

    @Override
    public String name() {
        return "get_profile_and_gear";
    }

    @Override
    public String description() {
        return "현재 프로필과 장비를 조회한다: 진행 중인 체성분 목표, FTP·최대심박, 최근 신체 치수(발 길이·볼, 신발 모델·사이즈·인솔 포함), "
                + "사용 중인 자전거와 자전거별 최신 피팅(안장 높이·셋백·리치·드롭·크랭크·클릿). "
                + "자세·통증·장비·신발·피팅 질문에 먼저 부른다.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolInputs.schema(Map.of(), List.of());
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        String text = contextService.profileAndGear(userId).stripTrailing();
        return text.length() <= MAX_RESULT_CHARS ? text : text.substring(0, MAX_RESULT_CHARS) + "\n(길어서 잘림)";
    }
}

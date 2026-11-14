package com.lifemetrics.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 애슐리트 챗 응답. toolCalls 는 tools 모드에서 답의 근거로 조회한 도구(이름·입력)이며 context 모드에서는 빈 목록. */
public record AthleteChatResponse(String reply, List<ToolCall> toolCalls) {
    public record ToolCall(String name, JsonNode input, boolean error) {}
}

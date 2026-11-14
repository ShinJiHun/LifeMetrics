package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;

/**
 * 애슐리트 챗(tool-use 모드)에서 모델이 부르는 읽기 전용 조회 도구.
 * user_id 는 입력에 없다: 서버가 로그인 사용자로 고정해서 넘긴다.
 * 결과는 한 줄에 한 건인 텍스트(기존 AthleteContextService 포맷)로 돌려준다.
 */
public interface AthleteTool {

    /** 도구 한 번 응답의 최대 길이. 넘으면 자르고 안내를 붙인다. */
    int MAX_RESULT_CHARS = 8_000;

    String name();

    String description();

    /** Anthropic tools[].input_schema (JSON Schema object). */
    Map<String, Object> inputSchema();

    /** 잘못된 입력은 IllegalArgumentException 으로 알린다(모델에 is_error 로 돌아가 스스로 고친다). */
    String execute(JsonNode input, long userId, LocalDate today);
}

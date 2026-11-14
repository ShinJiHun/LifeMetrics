package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 도구 입력 파싱·스키마·결과 포맷 공통 함수. */
final class ToolInputs {

    private ToolInputs() {}

    static Map<String, Object> dateProp(String description) {
        return Map.of("type", "string", "format", "date", "description", description + " (YYYY-MM-DD, 한국 시각)");
    }

    static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        if (!required.isEmpty()) s.put("required", required);
        return s;
    }

    /** 값이 없으면 fallback, 형식이 틀리면 IllegalArgumentException. */
    static LocalDate date(JsonNode input, String field, LocalDate fallback) {
        JsonNode v = input == null ? null : input.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            if (fallback == null) throw new IllegalArgumentException(field + " 는 필수입니다 (YYYY-MM-DD).");
            return fallback;
        }
        try {
            return LocalDate.parse(v.asText().trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " 형식이 잘못됐습니다: '" + v.asText() + "' (YYYY-MM-DD).");
        }
    }

    static String text(JsonNode input, String field) {
        JsonNode v = input == null ? null : input.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }

    static int intIn(JsonNode input, String field, int fallback, int min, int max) {
        JsonNode v = input == null ? null : input.get(field);
        if (v == null || v.isNull()) return fallback;
        if (!v.canConvertToInt()) throw new IllegalArgumentException(field + " 는 정수여야 합니다.");
        return Math.max(min, Math.min(max, v.asInt()));
    }

    static void requireOrder(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("from(" + from + ") 이 to(" + to + ") 보다 늦습니다.");
    }

    /** 헤더 + 한 줄에 한 건. 비면 "(기록 없음)", 길면 MAX_RESULT_CHARS 에서 자르고 안내. */
    static String lines(String header, List<String> lines, String truncateHint) {
        StringBuilder sb = new StringBuilder(header).append('\n');
        if (lines.isEmpty()) return sb.append("(기록 없음)").toString();
        int shown = 0;
        for (String l : lines) {
            if (sb.length() + l.length() + 3 > AthleteTool.MAX_RESULT_CHARS) break;
            sb.append("- ").append(l).append('\n');
            shown++;
        }
        if (shown < lines.size()) {
            sb.append("(").append(lines.size()).append("건 중 ").append(shown).append("건만 표시. ")
                    .append(truncateHint).append(")");
        }
        return sb.toString().stripTrailing();
    }
}

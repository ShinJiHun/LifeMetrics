package com.lifemetrics.backend.dto;

import java.util.List;
import java.util.Map;

public record RunPodResponse(
        String id,
        String status,
        List<Map<String, Object>> output,
        Long delayTime,
        Long executionTime
) {
    @SuppressWarnings("unchecked")
    public String extractText() {
        if (output == null || output.isEmpty()) return null;
        var choices = (List<Map<String, Object>>) output.get(0).get("choices");
        if (choices == null || choices.isEmpty()) return null;
        return (String) choices.get(0).get("text");
    }
}
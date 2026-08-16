package com.lifemetrics.backend.dto;

import java.util.Map;

public record RunPodRequest(Map<String, Object> input) {
    public static RunPodRequest ofPrompt(String prompt) {
        return new RunPodRequest(Map.of("prompt", prompt));
    }
}
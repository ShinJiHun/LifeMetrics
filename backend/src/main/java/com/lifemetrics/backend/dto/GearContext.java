package com.lifemetrics.backend.dto;

import java.util.Map;

public record GearContext(
        Long bikeId,
        String bikeLabel,        // "예거마리온 D8 2021"
        String chainring,        // "43/30"
        String cassette,         // "10-33"
        String tire,             // "700x25C"
        Map<String, String> etc  // CHAIN, WHEEL 등 기타 확장
) {}
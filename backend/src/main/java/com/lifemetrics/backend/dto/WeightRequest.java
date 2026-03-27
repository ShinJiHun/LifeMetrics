package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.Setter;

// WeightRequest.java
@Getter
@Setter
public class WeightRequest {
    private Long userId;
    private String recordDate;  // "2026-03-26"
    private Double weight;
    private Double bodyFatPercentage;
}

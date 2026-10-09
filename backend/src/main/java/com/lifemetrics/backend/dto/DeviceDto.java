package com.lifemetrics.backend.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 센서·기기 (device_info 중 BIKE 를 제외한 것).
 * startDate/endDate 는 device_info.first_seen_at/last_seen_at 을 날짜로 쓴 것 — AI 분석이
 * 라이딩 날짜에 사용 중이던 기기만 골라낼 때 쓴다.
 */
@Data
public class DeviceDto {
    private Long id;
    private String deviceType;      // HEAD_UNIT, SPEED_SENSOR, CADENCE_SENSOR, HEART_RATE, POWER_METER
    private String manufacturer;
    private String model;
    private String userLabel;
    private String serialNumber;
    private String firmwareVersion;
    private Boolean isActive;
    private LocalDate startDate;
    private LocalDate endDate;
}

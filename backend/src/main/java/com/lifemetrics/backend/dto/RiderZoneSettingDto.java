package com.lifemetrics.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 존 계산에 실제로 쓰이는 기준값.
 * ftpEstimated / maxHrEstimated 가 true 면 저장된 값이 없어 추정값을 쓴 것.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiderZoneSettingDto {
    private Integer ftp;
    private Integer maxHr;
    private boolean ftpEstimated;
    private boolean maxHrEstimated;
}

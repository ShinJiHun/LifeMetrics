// dto/ActivityPointDto.java
package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ActivityPointDto {
    private Integer seq;
    private LocalDateTime pointTime;
    private Double lat;
    private Double lon;
    private Double altitude;
    private Double distance;
    private Double speed;
    private Double cadence;
    private Double heartRate;
    private Double power;
    private Double slope;
    private Double heading;
}
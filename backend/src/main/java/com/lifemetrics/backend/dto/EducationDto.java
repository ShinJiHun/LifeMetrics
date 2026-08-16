package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EducationDto {
    private Long id;
    private String periodLabel;
    private String school;
    private String major;
    private Integer displayOrder;
}

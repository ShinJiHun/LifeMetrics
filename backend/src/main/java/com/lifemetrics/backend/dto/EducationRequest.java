package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EducationRequest {
    private String periodLabel;
    private String school;
    private String major;
    private Integer sortOrder;
}

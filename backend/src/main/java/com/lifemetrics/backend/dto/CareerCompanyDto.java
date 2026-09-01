package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class CareerCompanyDto {
    private Long id;
    private String path;
    private String domain;
    private String companyName;
    private String shortName;
    private String periodLabel;
    private LocalDate startDate;
    private LocalDate endDate;
    private String role;
    private String leaveReason;
    private Boolean isCurrent;
    private String commitHash;
    private String commitTag;
    private List<String> stack;
    private Integer displayOrder;
    private List<CareerProjectDto> projects;
}

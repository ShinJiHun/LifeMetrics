package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CareerCompanyDto {
    private Long id;
    private String path;
    private String companyName;
    private String periodLabel;
    private String role;
    private Boolean isCurrent;
    private String commitHash;
    private String commitTag;
    private List<String> stack;
    private Integer displayOrder;
    private List<CareerProjectDto> projects;
}

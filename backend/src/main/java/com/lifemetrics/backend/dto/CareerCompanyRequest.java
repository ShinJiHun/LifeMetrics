package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CareerCompanyRequest {
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
    private Integer sortOrder;
}

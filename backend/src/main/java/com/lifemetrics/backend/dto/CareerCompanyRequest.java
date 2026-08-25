package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CareerCompanyRequest {
    private String path;
    private String domain;
    private String companyName;
    private String periodLabel;
    private String role;
    private Boolean isCurrent;
    private String commitHash;
    private String commitTag;
    private List<String> stack;
    private Integer sortOrder;
}

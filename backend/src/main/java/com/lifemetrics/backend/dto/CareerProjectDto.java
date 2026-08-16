package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CareerProjectDto {
    private Long id;
    private Long companyId;
    private String title;
    private String periodLabel;
    private List<String> paragraphs;
    private Integer sortOrder;
}

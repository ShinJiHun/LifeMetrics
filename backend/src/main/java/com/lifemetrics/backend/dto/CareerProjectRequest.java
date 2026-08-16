package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CareerProjectRequest {
    private Long companyId;
    private String title;
    private String periodLabel;
    private List<String> paragraphs;
    private Integer sortOrder;
}

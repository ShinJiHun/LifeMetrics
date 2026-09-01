package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PersonalProjectDto {
    private Long id;
    private String kind; // FEATURED | MINI
    private String title;
    private String blurb;
    private String repoUrl;
    private String periodLabel;
    private List<String> tags;
    private Integer sortOrder;
    private List<PersonalProjectFeatureDto> features;
}

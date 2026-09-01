package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PersonalProjectFeatureDto {
    private Long id;
    private Long projectId;
    private String icon;
    private String title;
    private String description;
    private List<String> tags;
    private Integer sortOrder;
}

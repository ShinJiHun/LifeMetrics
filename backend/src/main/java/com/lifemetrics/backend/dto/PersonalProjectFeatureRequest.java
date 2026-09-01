package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PersonalProjectFeatureRequest {
    private Long projectId;
    private String icon;
    private String title;
    private String description;
    private List<String> tags;
    private Integer sortOrder;
}

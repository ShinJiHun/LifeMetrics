package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PersonalProjectRequest {
    private String kind; // FEATURED | MINI
    private String title;
    private String blurb;
    private String repoUrl;
    private String periodLabel;
    private List<String> tags;
    private Integer sortOrder;
}

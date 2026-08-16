package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class IntroSectionRequest {
    private String subtitle;
    private List<String> lines;
    private Integer sortOrder;
}

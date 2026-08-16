package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProfileIntroSectionDto {
    private Long id;
    private String subtitle;
    private List<String> lines;
    private Integer sortOrder;
}

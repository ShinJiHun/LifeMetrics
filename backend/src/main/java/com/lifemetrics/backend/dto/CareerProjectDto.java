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
    private String overview; // 블로그 글처럼 작성하는 리치 텍스트(HTML) 또는 마크다운
    private Integer sortOrder;
    private List<CareerProjectTaskDto> tasks;
}

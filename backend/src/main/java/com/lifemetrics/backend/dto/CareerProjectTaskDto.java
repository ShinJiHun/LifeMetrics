package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CareerProjectTaskDto {
    private Long id;
    private Long projectId;
    private String description;
    private Integer sortOrder;
    private List<CareerTaskMediaDto> media;
}

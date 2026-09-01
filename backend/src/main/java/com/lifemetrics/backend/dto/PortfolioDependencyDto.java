package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PortfolioDependencyDto {
    private Long id;
    private String category;
    private String depKey;
    private String note;
    private Integer sortOrder;
}

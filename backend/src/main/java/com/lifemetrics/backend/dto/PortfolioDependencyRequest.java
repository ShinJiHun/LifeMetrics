package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioDependencyRequest {
    private String category;
    private String depKey;
    private String note;
    private Integer sortOrder;
}

package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PortfolioTroubleshootDto {
    private Long id;
    private String refLabel;
    private String title;
    private List<String> removed;
    private List<String> added;
    private Integer sortOrder;
}

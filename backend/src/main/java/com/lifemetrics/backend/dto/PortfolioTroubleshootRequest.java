package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioTroubleshootRequest {
    private String refLabel;
    private String title;
    private List<String> removed;
    private List<String> added;
    private Integer sortOrder;
}

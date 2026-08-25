package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CareerProjectTaskRequest {
    private Long projectId;
    private String description;
    private Integer sortOrder;
}

package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CareerTaskMediaDto {
    private Long id;
    private Long taskId;
    private String url;
    private String mediaKind; // IMAGE | VIDEO
    private Integer sortOrder;
}

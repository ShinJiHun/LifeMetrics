package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProfileContactDto {
    private String phone;
    private String github;
    private String blog;
}

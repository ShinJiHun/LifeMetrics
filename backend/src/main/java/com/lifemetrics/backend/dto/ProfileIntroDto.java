package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProfileIntroDto {
    private String elevatorPitch;
    private List<String> highlights;
    private List<ProfileIntroSectionDto> sections;
}

package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProfileIntroDto {
    private String elevatorPitch;
    private List<String> highlights;
    private String headline;
    private String subheadline;
    private List<ProfileIntroSectionDto> sections;
    private String roleTagline;
    private List<String> focusTags;
    private String contactBlurb;
    private String sideProject;
    private String availability;
    private boolean openToWork;
}

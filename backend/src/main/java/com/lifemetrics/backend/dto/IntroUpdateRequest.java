package com.lifemetrics.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class IntroUpdateRequest {
    private String elevatorPitch;
    private List<String> highlights;
    private String headline;
    private String subheadline;
}

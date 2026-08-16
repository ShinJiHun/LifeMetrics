package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProfileDto {
    private ProfileIntroDto intro;
    private ProfileContactDto contact;
    private List<CareerCompanyDto> career;
    private List<EducationDto> education;
}

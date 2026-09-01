package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProfileDto {
    private ProfileIntroDto intro;
    private ProfileContactDto contact;
    private ProfileStatsDto stats;
    private List<CareerCompanyDto> career;
    private List<EducationDto> education;
    private List<PersonalProjectDto> personalProjects;
    private List<PortfolioTroubleshootDto> troubleshoots;
    private List<PortfolioDependencyDto> dependencies;
}

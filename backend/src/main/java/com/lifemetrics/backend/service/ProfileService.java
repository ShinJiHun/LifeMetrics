package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.*;
import com.lifemetrics.backend.persona.entity.CareerCompany;
import com.lifemetrics.backend.persona.entity.CareerProject;
import com.lifemetrics.backend.persona.entity.DeveloperProfile;
import com.lifemetrics.backend.persona.entity.Education;
import com.lifemetrics.backend.persona.entity.ProfileIntroSection;
import com.lifemetrics.backend.persona.repository.CareerCompanyRepository;
import com.lifemetrics.backend.persona.repository.CareerProjectRepository;
import com.lifemetrics.backend.persona.repository.DeveloperProfileRepository;
import com.lifemetrics.backend.persona.repository.EducationRepository;
import com.lifemetrics.backend.persona.repository.ProfileIntroSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 개발자 포트폴리오 프로필(소개/연락처/경력/학력) CRUD.
 * developer_profile 은 단일 행만 사용한다 (없으면 최초 쓰기 시점에 생성).
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final DeveloperProfileRepository developerProfileRepository;
    private final ProfileIntroSectionRepository introSectionRepository;
    private final CareerCompanyRepository careerCompanyRepository;
    private final CareerProjectRepository careerProjectRepository;
    private final EducationRepository educationRepository;

    // ── 조회 ─────────────────────────────────────────────────────
    @Transactional(value = "journalTransactionManager", readOnly = true)
    public ProfileDto getProfile() {
        DeveloperProfile dp = developerProfileRepository.findAll().stream().findFirst().orElse(null);

        List<ProfileIntroSectionDto> sections = introSectionRepository.findAllByOrderBySortOrderAsc()
                .stream().map(this::toDto).toList();

        List<CareerProject> allProjects = careerProjectRepository.findAllByOrderBySortOrderAsc();
        List<CareerCompanyDto> career = careerCompanyRepository.findAllByOrderBySortOrderAsc().stream()
                .map(c -> toDto(c, allProjects.stream()
                        .filter(p -> p.getCompanyId().equals(c.getId()))
                        .map(this::toDto)
                        .toList()))
                .toList();

        List<EducationDto> education = educationRepository.findAllByOrderBySortOrderAsc()
                .stream().map(this::toDto).toList();

        return ProfileDto.builder()
                .intro(ProfileIntroDto.builder()
                        .elevatorPitch(dp != null ? dp.getElevatorPitch() : "")
                        .highlights(dp != null ? splitLines(dp.getHighlights()) : Collections.emptyList())
                        .sections(sections)
                        .build())
                .contact(ProfileContactDto.builder()
                        .phone(dp != null ? dp.getPhone() : "")
                        .github(dp != null ? dp.getGithub() : "")
                        .blog(dp != null ? dp.getBlog() : "")
                        .build())
                .career(career)
                .education(education)
                .build();
    }

    // ── 소개 ─────────────────────────────────────────────────────
    @Transactional("journalTransactionManager")
    public void updateIntro(IntroUpdateRequest req) {
        DeveloperProfile dp = getOrCreateDeveloperProfile();
        dp.setElevatorPitch(req.getElevatorPitch());
        dp.setHighlights(joinLines(req.getHighlights()));
        developerProfileRepository.save(dp);
    }

    @Transactional("journalTransactionManager")
    public ProfileIntroSectionDto addIntroSection(IntroSectionRequest req) {
        ProfileIntroSection s = new ProfileIntroSection();
        applyIntroSection(s, req);
        return toDto(introSectionRepository.save(s));
    }

    @Transactional("journalTransactionManager")
    public void updateIntroSection(Long id, IntroSectionRequest req) {
        ProfileIntroSection s = introSectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("소개 섹션을 찾을 수 없습니다: " + id));
        applyIntroSection(s, req);
        introSectionRepository.save(s);
    }

    @Transactional("journalTransactionManager")
    public void deleteIntroSection(Long id) {
        introSectionRepository.deleteById(id);
    }

    // ── 연락처 ─────────────────────────────────────────────────────
    @Transactional("journalTransactionManager")
    public void updateContact(ContactUpdateRequest req) {
        DeveloperProfile dp = getOrCreateDeveloperProfile();
        dp.setPhone(req.getPhone());
        dp.setGithub(req.getGithub());
        dp.setBlog(req.getBlog());
        developerProfileRepository.save(dp);
    }

    // ── 경력(회사) ─────────────────────────────────────────────────
    @Transactional("journalTransactionManager")
    public CareerCompanyDto addCareerCompany(CareerCompanyRequest req) {
        CareerCompany c = new CareerCompany();
        applyCareerCompany(c, req);
        c = careerCompanyRepository.save(c);
        return toDto(c, Collections.emptyList());
    }

    @Transactional("journalTransactionManager")
    public void updateCareerCompany(Long id, CareerCompanyRequest req) {
        CareerCompany c = careerCompanyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + id));
        applyCareerCompany(c, req);
        careerCompanyRepository.save(c);
    }

    @Transactional("journalTransactionManager")
    public void deleteCareerCompany(Long id) {
        // DB의 FK ON DELETE CASCADE 가 career_project 를 함께 정리한다.
        careerCompanyRepository.deleteById(id);
    }

    // ── 경력(프로젝트) ─────────────────────────────────────────────
    @Transactional("journalTransactionManager")
    public CareerProjectDto addCareerProject(CareerProjectRequest req) {
        CareerProject p = new CareerProject();
        applyCareerProject(p, req);
        return toDto(careerProjectRepository.save(p));
    }

    @Transactional("journalTransactionManager")
    public void updateCareerProject(Long id, CareerProjectRequest req) {
        CareerProject p = careerProjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("프로젝트를 찾을 수 없습니다: " + id));
        applyCareerProject(p, req);
        careerProjectRepository.save(p);
    }

    @Transactional("journalTransactionManager")
    public void deleteCareerProject(Long id) {
        careerProjectRepository.deleteById(id);
    }

    // ── 학력 ─────────────────────────────────────────────────────
    @Transactional("journalTransactionManager")
    public EducationDto addEducation(EducationRequest req) {
        Education e = new Education();
        applyEducation(e, req);
        return toDto(educationRepository.save(e));
    }

    @Transactional("journalTransactionManager")
    public void updateEducation(Long id, EducationRequest req) {
        Education e = educationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("학력을 찾을 수 없습니다: " + id));
        applyEducation(e, req);
        educationRepository.save(e);
    }

    @Transactional("journalTransactionManager")
    public void deleteEducation(Long id) {
        educationRepository.deleteById(id);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────
    private DeveloperProfile getOrCreateDeveloperProfile() {
        return developerProfileRepository.findAll().stream().findFirst()
                .orElseGet(DeveloperProfile::new);
    }

    private void applyIntroSection(ProfileIntroSection s, IntroSectionRequest req) {
        s.setSubtitle(req.getSubtitle());
        s.setLines(joinLines(req.getLines()));
        s.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
    }

    private void applyCareerCompany(CareerCompany c, CareerCompanyRequest req) {
        c.setPath(req.getPath());
        c.setCompanyName(req.getCompanyName());
        c.setPeriodLabel(req.getPeriodLabel());
        c.setRole(req.getRole());
        c.setIsCurrent(Boolean.TRUE.equals(req.getIsCurrent()));
        c.setCommitHash(req.getCommitHash());
        c.setCommitTag(req.getCommitTag());
        c.setStack(req.getStack() != null ? String.join(",", req.getStack()) : null);
        c.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
    }

    private void applyCareerProject(CareerProject p, CareerProjectRequest req) {
        p.setCompanyId(req.getCompanyId());
        p.setTitle(req.getTitle());
        p.setPeriodLabel(req.getPeriodLabel());
        p.setParagraphs(req.getParagraphs() != null ? String.join("\n\n", req.getParagraphs()) : "");
        p.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
    }

    private void applyEducation(Education e, EducationRequest req) {
        e.setPeriodLabel(req.getPeriodLabel());
        e.setSchool(req.getSchool());
        e.setMajor(req.getMajor());
        e.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
    }

    private ProfileIntroSectionDto toDto(ProfileIntroSection s) {
        return ProfileIntroSectionDto.builder()
                .id(s.getId())
                .subtitle(s.getSubtitle())
                .lines(splitLines(s.getLines()))
                .sortOrder(s.getSortOrder())
                .build();
    }

    private CareerCompanyDto toDto(CareerCompany c, List<CareerProjectDto> projects) {
        return CareerCompanyDto.builder()
                .id(c.getId())
                .path(c.getPath())
                .companyName(c.getCompanyName())
                .periodLabel(c.getPeriodLabel())
                .role(c.getRole())
                .isCurrent(c.getIsCurrent())
                .commitHash(c.getCommitHash())
                .commitTag(c.getCommitTag())
                .stack(splitComma(c.getStack()))
                .displayOrder(c.getSortOrder())
                .projects(projects)
                .build();
    }

    private CareerProjectDto toDto(CareerProject p) {
        return CareerProjectDto.builder()
                .id(p.getId())
                .companyId(p.getCompanyId())
                .title(p.getTitle())
                .periodLabel(p.getPeriodLabel())
                .paragraphs(splitParagraphs(p.getParagraphs()))
                .sortOrder(p.getSortOrder())
                .build();
    }

    private EducationDto toDto(Education e) {
        return EducationDto.builder()
                .id(e.getId())
                .periodLabel(e.getPeriodLabel())
                .school(e.getSchool())
                .major(e.getMajor())
                .displayOrder(e.getSortOrder())
                .build();
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        return Arrays.stream(text.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private List<String> splitComma(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private List<String> splitParagraphs(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        return Arrays.stream(text.split("\n\\s*\n")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private String joinLines(List<String> lines) {
        return lines != null ? String.join("\n", lines) : "";
    }
}

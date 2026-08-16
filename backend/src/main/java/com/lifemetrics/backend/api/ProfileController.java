package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.*;
import com.lifemetrics.backend.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 개발자 포트폴리오(PersonaPortfolioPage) 프로필 조회/관리(ProfileManagePage) API.
 * 쓰기 요청은 AdminWriteFilter 가 일괄적으로 관리자 전용으로 막는다.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileDto getProfile() {
        return profileService.getProfile();
    }

    // ── 소개 ─────────────────────────────────────────────────────
    @PutMapping("/intro")
    public void updateIntro(@RequestBody IntroUpdateRequest req) {
        profileService.updateIntro(req);
    }

    @PostMapping("/intro/sections")
    public ProfileIntroSectionDto addIntroSection(@RequestBody IntroSectionRequest req) {
        return profileService.addIntroSection(req);
    }

    @PutMapping("/intro/sections/{id}")
    public void updateIntroSection(@PathVariable Long id, @RequestBody IntroSectionRequest req) {
        profileService.updateIntroSection(id, req);
    }

    @DeleteMapping("/intro/sections/{id}")
    public void deleteIntroSection(@PathVariable Long id) {
        profileService.deleteIntroSection(id);
    }

    // ── 연락처 ─────────────────────────────────────────────────────
    @PutMapping("/contact")
    public void updateContact(@RequestBody ContactUpdateRequest req) {
        profileService.updateContact(req);
    }

    // ── 경력(회사) ─────────────────────────────────────────────────
    @PostMapping("/career")
    public CareerCompanyDto addCareerCompany(@RequestBody CareerCompanyRequest req) {
        return profileService.addCareerCompany(req);
    }

    @PutMapping("/career/{id}")
    public void updateCareerCompany(@PathVariable Long id, @RequestBody CareerCompanyRequest req) {
        profileService.updateCareerCompany(id, req);
    }

    @DeleteMapping("/career/{id}")
    public void deleteCareerCompany(@PathVariable Long id) {
        profileService.deleteCareerCompany(id);
    }

    // ── 경력(프로젝트) ─────────────────────────────────────────────
    @PostMapping("/career/projects")
    public CareerProjectDto addCareerProject(@RequestBody CareerProjectRequest req) {
        return profileService.addCareerProject(req);
    }

    @PutMapping("/career/projects/{id}")
    public void updateCareerProject(@PathVariable Long id, @RequestBody CareerProjectRequest req) {
        profileService.updateCareerProject(id, req);
    }

    @DeleteMapping("/career/projects/{id}")
    public void deleteCareerProject(@PathVariable Long id) {
        profileService.deleteCareerProject(id);
    }

    // ── 학력 ─────────────────────────────────────────────────────
    @PostMapping("/education")
    public EducationDto addEducation(@RequestBody EducationRequest req) {
        return profileService.addEducation(req);
    }

    @PutMapping("/education/{id}")
    public void updateEducation(@PathVariable Long id, @RequestBody EducationRequest req) {
        profileService.updateEducation(id, req);
    }

    @DeleteMapping("/education/{id}")
    public void deleteEducation(@PathVariable Long id) {
        profileService.deleteEducation(id);
    }
}

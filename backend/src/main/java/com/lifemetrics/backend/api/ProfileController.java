package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.*;
import com.lifemetrics.backend.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 개발자 포트폴리오(PersonaPortfolioPage) 프로필 조회/관리(ProfileManagePage) API.
 * 쓰기 요청은 AdminWriteFilter 가 일괄적으로 관리자 전용으로 막는다.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Value("${career.media.path:/tmp/career-media/}")
    private String careerMediaPath;

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

    // ── 경력(업무) ─────────────────────────────────────────────────
    @PostMapping("/career/tasks")
    public CareerProjectTaskDto addCareerProjectTask(@RequestBody CareerProjectTaskRequest req) {
        return profileService.addCareerProjectTask(req);
    }

    @PutMapping("/career/tasks/{id}")
    public void updateCareerProjectTask(@PathVariable Long id, @RequestBody CareerProjectTaskRequest req) {
        profileService.updateCareerProjectTask(id, req);
    }

    @DeleteMapping("/career/tasks/{id}")
    public void deleteCareerProjectTask(@PathVariable Long id) {
        profileService.deleteCareerProjectTask(id);
    }

    // ── 경력(업무 미디어) ─────────────────────────────────────────
    @PostMapping("/career/tasks/{taskId}/media")
    public ResponseEntity<CareerTaskMediaDto> uploadTaskMedia(@PathVariable Long taskId, @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(profileService.uploadTaskMedia(taskId, file));
    }

    @DeleteMapping("/career/tasks/media/{mediaId}")
    public void deleteTaskMedia(@PathVariable Long mediaId) {
        profileService.deleteTaskMedia(mediaId);
    }

    // 파일명에 경로 구분자가 없는지(디렉터리 탈출 방지) 확인 후 서빙한다.
    @GetMapping("/career/media/{filename}")
    public ResponseEntity<Resource> getTaskMedia(@PathVariable String filename) {
        if (filename.contains("/") || filename.contains("..")) return ResponseEntity.badRequest().build();
        Path path = Paths.get(careerMediaPath, filename);
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        MediaType contentType = filename.toLowerCase().endsWith(".mp4") ? MediaType.valueOf("video/mp4")
                : filename.toLowerCase().endsWith(".mov") ? MediaType.valueOf("video/quicktime")
                : filename.toLowerCase().endsWith(".webm") ? MediaType.valueOf("video/webm")
                : filename.toLowerCase().endsWith(".gif") ? MediaType.IMAGE_GIF
                : filename.toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header("Cache-Control", "public, max-age=86400")
                .body(new FileSystemResource(path));
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

    // ── 개인 프로젝트(05 섹션) ────────────────────────────────────
    @PostMapping("/personal-projects")
    public PersonalProjectDto addPersonalProject(@RequestBody PersonalProjectRequest req) {
        return profileService.addPersonalProject(req);
    }

    @PutMapping("/personal-projects/{id}")
    public void updatePersonalProject(@PathVariable Long id, @RequestBody PersonalProjectRequest req) {
        profileService.updatePersonalProject(id, req);
    }

    @DeleteMapping("/personal-projects/{id}")
    public void deletePersonalProject(@PathVariable Long id) {
        profileService.deletePersonalProject(id);
    }

    @PostMapping("/personal-projects/features")
    public PersonalProjectFeatureDto addPersonalProjectFeature(@RequestBody PersonalProjectFeatureRequest req) {
        return profileService.addPersonalProjectFeature(req);
    }

    @PutMapping("/personal-projects/features/{id}")
    public void updatePersonalProjectFeature(@PathVariable Long id, @RequestBody PersonalProjectFeatureRequest req) {
        profileService.updatePersonalProjectFeature(id, req);
    }

    @DeleteMapping("/personal-projects/features/{id}")
    public void deletePersonalProjectFeature(@PathVariable Long id) {
        profileService.deletePersonalProjectFeature(id);
    }

    // ── 트러블슈팅 로그(05 섹션) ─────────────────────────────────
    @PostMapping("/troubleshoots")
    public PortfolioTroubleshootDto addTroubleshoot(@RequestBody PortfolioTroubleshootRequest req) {
        return profileService.addTroubleshoot(req);
    }

    @PutMapping("/troubleshoots/{id}")
    public void updateTroubleshoot(@PathVariable Long id, @RequestBody PortfolioTroubleshootRequest req) {
        profileService.updateTroubleshoot(id, req);
    }

    @DeleteMapping("/troubleshoots/{id}")
    public void deleteTroubleshoot(@PathVariable Long id) {
        profileService.deleteTroubleshoot(id);
    }

    // ── 의존성 목록(05 섹션) ─────────────────────────────────────
    @PostMapping("/dependencies")
    public PortfolioDependencyDto addDependency(@RequestBody PortfolioDependencyRequest req) {
        return profileService.addDependency(req);
    }

    @PutMapping("/dependencies/{id}")
    public void updateDependency(@PathVariable Long id, @RequestBody PortfolioDependencyRequest req) {
        profileService.updateDependency(id, req);
    }

    @DeleteMapping("/dependencies/{id}")
    public void deleteDependency(@PathVariable Long id) {
        profileService.deleteDependency(id);
    }
}

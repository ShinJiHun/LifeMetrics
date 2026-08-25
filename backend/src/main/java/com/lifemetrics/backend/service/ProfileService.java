package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.*;
import com.lifemetrics.backend.persona.entity.CareerCompany;
import com.lifemetrics.backend.persona.entity.CareerProject;
import com.lifemetrics.backend.persona.entity.CareerProjectTask;
import com.lifemetrics.backend.persona.entity.CareerTaskMedia;
import com.lifemetrics.backend.persona.entity.DeveloperProfile;
import com.lifemetrics.backend.persona.entity.Education;
import com.lifemetrics.backend.persona.entity.ProfileIntroSection;
import com.lifemetrics.backend.persona.repository.CareerCompanyRepository;
import com.lifemetrics.backend.persona.repository.CareerProjectRepository;
import com.lifemetrics.backend.persona.repository.CareerProjectTaskRepository;
import com.lifemetrics.backend.persona.repository.CareerTaskMediaRepository;
import com.lifemetrics.backend.persona.repository.DeveloperProfileRepository;
import com.lifemetrics.backend.persona.repository.EducationRepository;
import com.lifemetrics.backend.persona.repository.ProfileIntroSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
    private final CareerProjectTaskRepository careerProjectTaskRepository;
    private final CareerTaskMediaRepository careerTaskMediaRepository;
    private final EducationRepository educationRepository;

    @Value("${career.media.path:/tmp/career-media/}")
    private String careerMediaPath;

    private static final String CAREER_MEDIA_URL_PREFIX = "/api/profile/career/media/";

    // 마이그레이션 전(=headline/subheadline 컬럼이 비어있는) 기존 배포를 위한 기본값
    private static final String DEFAULT_HEADLINE = "음성 데이터가 서버와 엔진 사이를\n{{끊기지 않고}} 흐르게 만듭니다.";
    private static final String DEFAULT_SUBHEADLINE =
            "웹 프론트엔드로 시작해 주차 시스템 백엔드, SIEM, 그리고 지금은 STT·gRPC·MRCP 기반 음성인식 인프라까지 — 도메인을 넓혀가며 8년 3개월째 만들고 운영하고 있습니다.";

    // ── 조회 ─────────────────────────────────────────────────────
    @Transactional(value = "journalTransactionManager", readOnly = true)
    public ProfileDto getProfile() {
        DeveloperProfile dp = developerProfileRepository.findAll().stream().findFirst().orElse(null);

        List<ProfileIntroSectionDto> sections = introSectionRepository.findAllByOrderBySortOrderAsc()
                .stream().map(this::toDto).toList();

        List<CareerProject> allProjects = careerProjectRepository.findAllByOrderBySortOrderAsc();
        List<CareerProjectTask> allTasks = careerProjectTaskRepository.findAllByOrderBySortOrderAsc();
        List<CareerTaskMedia> allMedia = careerTaskMediaRepository.findAllByOrderBySortOrderAsc();

        List<CareerCompanyDto> career = careerCompanyRepository.findAllByOrderBySortOrderAsc().stream()
                .map(c -> toDto(c, allProjects.stream()
                        .filter(p -> p.getCompanyId().equals(c.getId()))
                        .map(p -> toDto(p, allTasks.stream()
                                .filter(t -> t.getProjectId().equals(p.getId()))
                                .map(t -> toDto(t, allMedia.stream()
                                        .filter(m -> m.getTaskId().equals(t.getId()))
                                        .map(this::toDto)
                                        .toList()))
                                .toList()))
                        .toList()))
                .toList();

        List<EducationDto> education = educationRepository.findAllByOrderBySortOrderAsc()
                .stream().map(this::toDto).toList();

        return ProfileDto.builder()
                .intro(ProfileIntroDto.builder()
                        .elevatorPitch(dp != null ? dp.getElevatorPitch() : "")
                        .highlights(dp != null ? splitLines(dp.getHighlights()) : Collections.emptyList())
                        .headline(dp != null && dp.getHeadline() != null ? dp.getHeadline() : DEFAULT_HEADLINE)
                        .subheadline(dp != null && dp.getSubheadline() != null ? dp.getSubheadline() : DEFAULT_SUBHEADLINE)
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
        dp.setHeadline(req.getHeadline());
        dp.setSubheadline(req.getSubheadline());
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

    // ── 경력(업무) ─────────────────────────────────────────────────
    @Transactional("journalTransactionManager")
    public CareerProjectTaskDto addCareerProjectTask(CareerProjectTaskRequest req) {
        CareerProjectTask t = new CareerProjectTask();
        applyCareerProjectTask(t, req);
        return toDto(careerProjectTaskRepository.save(t));
    }

    @Transactional("journalTransactionManager")
    public void updateCareerProjectTask(Long id, CareerProjectTaskRequest req) {
        CareerProjectTask t = careerProjectTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("업무 항목을 찾을 수 없습니다: " + id));
        applyCareerProjectTask(t, req);
        careerProjectTaskRepository.save(t);
    }

    @Transactional("journalTransactionManager")
    public void deleteCareerProjectTask(Long id) {
        List<CareerTaskMedia> media = careerTaskMediaRepository.findByTaskIdOrderBySortOrderAsc(id);
        media.forEach(this::deleteMediaFile);
        careerTaskMediaRepository.deleteAll(media);
        careerProjectTaskRepository.deleteById(id);
    }

    // ── 경력(업무 미디어) ─────────────────────────────────────────
    @Transactional("journalTransactionManager")
    public CareerTaskMediaDto uploadTaskMedia(Long taskId, MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase() : "";
        String storedName = UUID.randomUUID() + ext;

        Path dir = Paths.get(careerMediaPath);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);

        int nextSort = careerTaskMediaRepository.findByTaskIdOrderBySortOrderAsc(taskId).size();

        CareerTaskMedia m = new CareerTaskMedia();
        m.setTaskId(taskId);
        m.setFilename(storedName);
        m.setMediaKind(isVideoExtension(ext) ? CareerTaskMedia.MediaKind.VIDEO : CareerTaskMedia.MediaKind.IMAGE);
        m.setSortOrder(nextSort);

        return toDto(careerTaskMediaRepository.save(m));
    }

    @Transactional("journalTransactionManager")
    public void deleteTaskMedia(Long mediaId) {
        CareerTaskMedia m = careerTaskMediaRepository.findById(mediaId).orElse(null);
        if (m == null) return;
        deleteMediaFile(m);
        careerTaskMediaRepository.deleteById(mediaId);
    }

    private void deleteMediaFile(CareerTaskMedia m) {
        try {
            Files.deleteIfExists(Paths.get(careerMediaPath, m.getFilename()));
        } catch (IOException ignored) {
            // 파일 정리 실패는 무시 — DB 레코드 삭제는 계속 진행한다.
        }
    }

    private boolean isVideoExtension(String ext) {
        return ext.equals(".mp4") || ext.equals(".mov") || ext.equals(".webm");
    }

    private void applyCareerProjectTask(CareerProjectTask t, CareerProjectTaskRequest req) {
        t.setProjectId(req.getProjectId());
        t.setDescription(req.getDescription());
        t.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
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
        c.setDomain(req.getDomain());
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
        p.setParagraphs(req.getOverview() != null ? req.getOverview() : "");
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
                .domain(c.getDomain())
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
        return toDto(p, Collections.emptyList());
    }

    private CareerProjectDto toDto(CareerProject p, List<CareerProjectTaskDto> tasks) {
        return CareerProjectDto.builder()
                .id(p.getId())
                .companyId(p.getCompanyId())
                .title(p.getTitle())
                .periodLabel(p.getPeriodLabel())
                .overview(p.getParagraphs())
                .sortOrder(p.getSortOrder())
                .tasks(tasks)
                .build();
    }

    private CareerProjectTaskDto toDto(CareerProjectTask t) {
        return toDto(t, Collections.emptyList());
    }

    private CareerProjectTaskDto toDto(CareerProjectTask t, List<CareerTaskMediaDto> media) {
        return CareerProjectTaskDto.builder()
                .id(t.getId())
                .projectId(t.getProjectId())
                .description(t.getDescription())
                .sortOrder(t.getSortOrder())
                .media(media)
                .build();
    }

    private CareerTaskMediaDto toDto(CareerTaskMedia m) {
        return CareerTaskMediaDto.builder()
                .id(m.getId())
                .taskId(m.getTaskId())
                .url(CAREER_MEDIA_URL_PREFIX + m.getFilename())
                .mediaKind(m.getMediaKind().name())
                .sortOrder(m.getSortOrder())
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

    private String joinLines(List<String> lines) {
        return lines != null ? String.join("\n", lines) : "";
    }
}

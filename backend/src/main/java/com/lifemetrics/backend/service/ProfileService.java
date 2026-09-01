package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.*;
import com.lifemetrics.backend.persona.entity.CareerCompany;
import com.lifemetrics.backend.persona.entity.CareerProject;
import com.lifemetrics.backend.persona.entity.CareerProjectTask;
import com.lifemetrics.backend.persona.entity.CareerTaskMedia;
import com.lifemetrics.backend.persona.entity.DeveloperProfile;
import com.lifemetrics.backend.persona.entity.Education;
import com.lifemetrics.backend.persona.entity.PersonalProject;
import com.lifemetrics.backend.persona.entity.PersonalProjectFeature;
import com.lifemetrics.backend.persona.entity.PortfolioDependency;
import com.lifemetrics.backend.persona.entity.PortfolioTroubleshoot;
import com.lifemetrics.backend.persona.entity.ProfileIntroSection;
import com.lifemetrics.backend.persona.repository.CareerCompanyRepository;
import com.lifemetrics.backend.persona.repository.CareerProjectRepository;
import com.lifemetrics.backend.persona.repository.CareerProjectTaskRepository;
import com.lifemetrics.backend.persona.repository.CareerTaskMediaRepository;
import com.lifemetrics.backend.persona.repository.DeveloperProfileRepository;
import com.lifemetrics.backend.persona.repository.EducationRepository;
import com.lifemetrics.backend.persona.repository.PersonalProjectFeatureRepository;
import com.lifemetrics.backend.persona.repository.PersonalProjectRepository;
import com.lifemetrics.backend.persona.repository.PortfolioDependencyRepository;
import com.lifemetrics.backend.persona.repository.PortfolioTroubleshootRepository;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private final PersonalProjectRepository personalProjectRepository;
    private final PersonalProjectFeatureRepository personalProjectFeatureRepository;
    private final PortfolioTroubleshootRepository portfolioTroubleshootRepository;
    private final PortfolioDependencyRepository portfolioDependencyRepository;

    @Value("${career.media.path:/tmp/career-media/}")
    private String careerMediaPath;

    private static final String CAREER_MEDIA_URL_PREFIX = "/api/profile/career/media/";

    // 마이그레이션 전(=headline/subheadline 컬럼이 비어있는) 기존 배포를 위한 기본값
    private static final String DEFAULT_HEADLINE = "음성 데이터가 서버와 엔진 사이를\n{{끊기지 않고}} 흐르게 만듭니다.";
    private static final String DEFAULT_SUBHEADLINE =
            "웹 프론트엔드로 시작해 주차 시스템 백엔드, SIEM, 그리고 지금은 STT·gRPC·MRCP 기반 음성인식 인프라까지 — 도메인을 넓혀가며 8년 3개월째 만들고 운영하고 있습니다.";
    private static final String DEFAULT_ROLE_TAGLINE = "Backend Developer";
    private static final String DEFAULT_FOCUS_TAGS = "STT,gRPC,MRCP";
    private static final String DEFAULT_CONTACT_BLURB =
            "음성인식 백엔드, 데이터 파이프라인, 인프라 운영에 관심 있는 팀이라면 언제든 편하게 연락 주세요.";
    private static final String DEFAULT_SIDE_PROJECT = "LifeMetrics";
    private static final String DEFAULT_AVAILABILITY = "이직 준비 중";
    private static final DateTimeFormatter SINCE_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // ── 조회 ─────────────────────────────────────────────────────
    @Transactional(value = "journalTransactionManager", readOnly = true)
    public ProfileDto getProfile() {
        DeveloperProfile dp = developerProfileRepository.findFirstByOrderByIdAsc().orElse(null);

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

        List<PersonalProjectFeature> allFeatures = personalProjectFeatureRepository.findAllByOrderBySortOrderAsc();
        List<PersonalProjectDto> personalProjects = personalProjectRepository.findAllByOrderBySortOrderAsc().stream()
                .map(p -> toDto(p, allFeatures.stream()
                        .filter(f -> f.getProjectId().equals(p.getId()))
                        .map(this::toDto)
                        .toList()))
                .toList();

        List<PortfolioTroubleshootDto> troubleshoots = portfolioTroubleshootRepository.findAllByOrderBySortOrderAsc()
                .stream().map(this::toDto).toList();

        List<PortfolioDependencyDto> dependencies = portfolioDependencyRepository.findAllByOrderBySortOrderAsc()
                .stream().map(this::toDto).toList();

        return ProfileDto.builder()
                .intro(ProfileIntroDto.builder()
                        .elevatorPitch(dp != null ? dp.getElevatorPitch() : "")
                        .highlights(dp != null ? splitLines(dp.getHighlights()) : Collections.emptyList())
                        .headline(dp != null && dp.getHeadline() != null ? dp.getHeadline() : DEFAULT_HEADLINE)
                        .subheadline(dp != null && dp.getSubheadline() != null ? dp.getSubheadline() : DEFAULT_SUBHEADLINE)
                        .sections(sections)
                        .roleTagline(dp != null && dp.getRoleTagline() != null ? dp.getRoleTagline() : DEFAULT_ROLE_TAGLINE)
                        .focusTags(dp != null && dp.getFocusTags() != null ? splitComma(dp.getFocusTags()) : splitComma(DEFAULT_FOCUS_TAGS))
                        .contactBlurb(dp != null && dp.getContactBlurb() != null ? dp.getContactBlurb() : DEFAULT_CONTACT_BLURB)
                        .sideProject(dp != null && dp.getSideProject() != null ? dp.getSideProject() : DEFAULT_SIDE_PROJECT)
                        .availability(dp != null && dp.getAvailability() != null ? dp.getAvailability() : DEFAULT_AVAILABILITY)
                        .openToWork(dp == null || dp.getOpenToWork() == null || dp.getOpenToWork())
                        .jobSearchNote(dp != null ? dp.getJobSearchNote() : null)
                        .build())
                .contact(ProfileContactDto.builder()
                        .phone(dp != null ? dp.getPhone() : "")
                        .github(dp != null ? dp.getGithub() : "")
                        .blog(dp != null ? dp.getBlog() : "")
                        .build())
                .stats(computeStats(career, allProjects.size()))
                .career(career)
                .education(education)
                .personalProjects(personalProjects)
                .troubleshoots(troubleshoots)
                .dependencies(dependencies)
                .build();
    }

    /**
     * 히어로/whoami 카드 집계값 계산.
     * - totalCareerMonths: 회사별 (start_date ~ end_date, 재직중이면 오늘) 개월 수를 시작·종료월 포함으로 합산
     * - currentCompany/currentSince: is_current=true 인 회사에서 추출
     */
    private ProfileStatsDto computeStats(List<CareerCompanyDto> career, int projectCount) {
        int totalMonths = 0;
        String currentCompany = null;
        String currentSince = null;

        for (CareerCompanyDto c : career) {
            if (c.getStartDate() != null) {
                LocalDate end = c.getEndDate() != null ? c.getEndDate() : LocalDate.now();
                YearMonth s = YearMonth.from(c.getStartDate());
                YearMonth e = YearMonth.from(end);
                if (!e.isBefore(s)) {
                    totalMonths += (int) ChronoUnit.MONTHS.between(s, e) + 1; // 시작·종료월 포함
                }
            }
            if (Boolean.TRUE.equals(c.getIsCurrent())) {
                currentCompany = c.getShortName() != null && !c.getShortName().isBlank()
                        ? c.getShortName() : c.getCompanyName();
                if (c.getStartDate() != null) {
                    currentSince = c.getStartDate().format(SINCE_FMT);
                }
            }
        }

        boolean employed = currentCompany != null;
        return ProfileStatsDto.builder()
                .totalCareerMonths(totalMonths)
                .totalCareerLabel(formatCareerLabel(totalMonths))
                .companyCount(career.size())
                .projectCount(projectCount)
                .employed(employed)
                .currentCompany(employed ? currentCompany : "")
                .currentSince(currentSince != null ? currentSince : "")
                .build();
    }

    private String formatCareerLabel(int months) {
        int years = months / 12;
        int rem = months % 12;
        if (years == 0) return rem + "개월";
        if (rem == 0) return years + "년";
        return years + "년 " + rem + "개월";
    }

    // ── 소개 ─────────────────────────────────────────────────────
    @Transactional("journalTransactionManager")
    public void updateIntro(IntroUpdateRequest req) {
        DeveloperProfile dp = getOrCreateDeveloperProfile();
        dp.setElevatorPitch(req.getElevatorPitch());
        dp.setHighlights(joinLines(req.getHighlights()));
        dp.setHeadline(req.getHeadline());
        dp.setSubheadline(req.getSubheadline());
        dp.setRoleTagline(req.getRoleTagline());
        dp.setFocusTags(req.getFocusTags() != null ? String.join(",", req.getFocusTags()) : null);
        dp.setContactBlurb(req.getContactBlurb());
        dp.setSideProject(req.getSideProject());
        dp.setAvailability(req.getAvailability());
        dp.setOpenToWork(req.getOpenToWork() == null || req.getOpenToWork());
        dp.setJobSearchNote(req.getJobSearchNote());
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

    // ── 개인 프로젝트(05 섹션) ────────────────────────────────────
    @Transactional("journalTransactionManager")
    public PersonalProjectDto addPersonalProject(PersonalProjectRequest req) {
        PersonalProject p = new PersonalProject();
        applyPersonalProject(p, req);
        return toDto(personalProjectRepository.save(p), Collections.emptyList());
    }

    @Transactional("journalTransactionManager")
    public void updatePersonalProject(Long id, PersonalProjectRequest req) {
        PersonalProject p = personalProjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("개인 프로젝트를 찾을 수 없습니다: " + id));
        applyPersonalProject(p, req);
        personalProjectRepository.save(p);
    }

    @Transactional("journalTransactionManager")
    public void deletePersonalProject(Long id) {
        personalProjectFeatureRepository.deleteByProjectId(id);
        personalProjectRepository.deleteById(id);
    }

    @Transactional("journalTransactionManager")
    public PersonalProjectFeatureDto addPersonalProjectFeature(PersonalProjectFeatureRequest req) {
        PersonalProjectFeature f = new PersonalProjectFeature();
        applyPersonalProjectFeature(f, req);
        return toDto(personalProjectFeatureRepository.save(f));
    }

    @Transactional("journalTransactionManager")
    public void updatePersonalProjectFeature(Long id, PersonalProjectFeatureRequest req) {
        PersonalProjectFeature f = personalProjectFeatureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("기능 카드를 찾을 수 없습니다: " + id));
        applyPersonalProjectFeature(f, req);
        personalProjectFeatureRepository.save(f);
    }

    @Transactional("journalTransactionManager")
    public void deletePersonalProjectFeature(Long id) {
        personalProjectFeatureRepository.deleteById(id);
    }

    // ── 트러블슈팅 로그(05 섹션) ─────────────────────────────────
    @Transactional("journalTransactionManager")
    public PortfolioTroubleshootDto addTroubleshoot(PortfolioTroubleshootRequest req) {
        PortfolioTroubleshoot t = new PortfolioTroubleshoot();
        applyTroubleshoot(t, req);
        return toDto(portfolioTroubleshootRepository.save(t));
    }

    @Transactional("journalTransactionManager")
    public void updateTroubleshoot(Long id, PortfolioTroubleshootRequest req) {
        PortfolioTroubleshoot t = portfolioTroubleshootRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("트러블슈팅 항목을 찾을 수 없습니다: " + id));
        applyTroubleshoot(t, req);
        portfolioTroubleshootRepository.save(t);
    }

    @Transactional("journalTransactionManager")
    public void deleteTroubleshoot(Long id) {
        portfolioTroubleshootRepository.deleteById(id);
    }

    // ── 의존성 목록(05 섹션) ─────────────────────────────────────
    @Transactional("journalTransactionManager")
    public PortfolioDependencyDto addDependency(PortfolioDependencyRequest req) {
        PortfolioDependency d = new PortfolioDependency();
        applyDependency(d, req);
        return toDto(portfolioDependencyRepository.save(d));
    }

    @Transactional("journalTransactionManager")
    public void updateDependency(Long id, PortfolioDependencyRequest req) {
        PortfolioDependency d = portfolioDependencyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("의존성 항목을 찾을 수 없습니다: " + id));
        applyDependency(d, req);
        portfolioDependencyRepository.save(d);
    }

    @Transactional("journalTransactionManager")
    public void deleteDependency(Long id) {
        portfolioDependencyRepository.deleteById(id);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────
    private DeveloperProfile getOrCreateDeveloperProfile() {
        return developerProfileRepository.findFirstByOrderByIdAsc()
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
        c.setShortName(req.getShortName());
        c.setPeriodLabel(req.getPeriodLabel());
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setRole(req.getRole());
        c.setLeaveReason(req.getLeaveReason());
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
                .shortName(c.getShortName())
                .periodLabel(c.getPeriodLabel())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .role(c.getRole())
                .leaveReason(c.getLeaveReason())
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

    // ── 05 섹션 apply / toDto ────────────────────────────────────
    private void applyPersonalProject(PersonalProject p, PersonalProjectRequest req) {
        p.setKind(req.getKind() != null && !req.getKind().isBlank() ? req.getKind().trim() : "MINI");
        p.setTitle(req.getTitle());
        p.setBlurb(req.getBlurb());
        p.setRepoUrl(req.getRepoUrl() != null && !req.getRepoUrl().isBlank() ? req.getRepoUrl().trim() : null);
        p.setPeriodLabel(req.getPeriodLabel() != null && !req.getPeriodLabel().isBlank() ? req.getPeriodLabel().trim() : null);
        p.setTags(req.getTags() != null ? String.join(",", req.getTags()) : null);
        p.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
    }

    private void applyPersonalProjectFeature(PersonalProjectFeature f, PersonalProjectFeatureRequest req) {
        f.setProjectId(req.getProjectId());
        f.setIcon(req.getIcon());
        f.setTitle(req.getTitle());
        f.setDescription(req.getDescription());
        f.setTags(req.getTags() != null ? String.join(",", req.getTags()) : null);
        f.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
    }

    private void applyTroubleshoot(PortfolioTroubleshoot t, PortfolioTroubleshootRequest req) {
        t.setRefLabel(req.getRefLabel());
        t.setTitle(req.getTitle());
        t.setRemovedLines(joinLines(req.getRemoved()));
        t.setAddedLines(joinLines(req.getAdded()));
        t.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
    }

    private void applyDependency(PortfolioDependency d, PortfolioDependencyRequest req) {
        d.setCategory(req.getCategory());
        d.setDepKey(req.getDepKey());
        d.setNote(req.getNote());
        d.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
    }

    private PersonalProjectDto toDto(PersonalProject p, List<PersonalProjectFeatureDto> features) {
        return PersonalProjectDto.builder()
                .id(p.getId())
                .kind(p.getKind())
                .title(p.getTitle())
                .blurb(p.getBlurb())
                .repoUrl(p.getRepoUrl())
                .periodLabel(p.getPeriodLabel())
                .tags(splitComma(p.getTags()))
                .sortOrder(p.getSortOrder())
                .features(features)
                .build();
    }

    private PersonalProjectFeatureDto toDto(PersonalProjectFeature f) {
        return PersonalProjectFeatureDto.builder()
                .id(f.getId())
                .projectId(f.getProjectId())
                .icon(f.getIcon())
                .title(f.getTitle())
                .description(f.getDescription())
                .tags(splitComma(f.getTags()))
                .sortOrder(f.getSortOrder())
                .build();
    }

    private PortfolioTroubleshootDto toDto(PortfolioTroubleshoot t) {
        return PortfolioTroubleshootDto.builder()
                .id(t.getId())
                .refLabel(t.getRefLabel())
                .title(t.getTitle())
                .removed(splitLines(t.getRemovedLines()))
                .added(splitLines(t.getAddedLines()))
                .sortOrder(t.getSortOrder())
                .build();
    }

    private PortfolioDependencyDto toDto(PortfolioDependency d) {
        return PortfolioDependencyDto.builder()
                .id(d.getId())
                .category(d.getCategory())
                .depKey(d.getDepKey())
                .note(d.getNote())
                .sortOrder(d.getSortOrder())
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

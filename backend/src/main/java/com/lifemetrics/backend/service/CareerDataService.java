package com.lifemetrics.backend.service;

import com.lifemetrics.backend.persona.entity.CareerCompany;
import com.lifemetrics.backend.persona.entity.CareerProject;
import com.lifemetrics.backend.persona.entity.DeveloperProfile;
import com.lifemetrics.backend.persona.repository.CareerCompanyRepository;
import com.lifemetrics.backend.persona.repository.CareerProjectRepository;
import com.lifemetrics.backend.persona.repository.DeveloperProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 페르소나 챗봇 프롬프트에 넣을 "경력" 컨텍스트를 정규화 테이블에서 만든다.
 * <p>
 * 예전엔 {@code user_basic_profile.career}(JSON blob)를 읽었으나, 포트폴리오 화면이
 * 쓰는 {@code career_company}/{@code career_project}/{@code developer_profile}와 내용이
 * 갈라져서 재직 여부 등이 낡은 값으로 주입됐다. 이제 그 정규화 테이블을 단일 소스로 쓴다.
 * 재직 여부·퇴사 사유·구직 상황은 여기(=DB)가 유일한 근거다.
 */
@Service
@RequiredArgsConstructor
public class CareerDataService {

    private final CareerCompanyRepository careerCompanyRepository;
    private final CareerProjectRepository careerProjectRepository;
    private final DeveloperProfileRepository developerProfileRepository;

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy.MM");

    @Transactional(value = "journalTransactionManager", readOnly = true)
    public boolean isLoaded() {
        return !careerCompanyRepository.findAll().isEmpty();
    }

    @Transactional(value = "journalTransactionManager", readOnly = true)
    public String getText() {
        List<CareerCompany> companies = careerCompanyRepository.findAllByOrderBySortOrderAsc();
        if (companies.isEmpty()) return "";

        DeveloperProfile dp = developerProfileRepository.findFirstByOrderByIdAsc().orElse(null);
        List<CareerProject> allProjects = careerProjectRepository.findAllByOrderBySortOrderAsc();

        StringBuilder sb = new StringBuilder();

        // ── 현재 상황 ──
        CareerCompany current = companies.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsCurrent()))
                .findFirst().orElse(null);
        sb.append("### 현재 상황\n");
        if (current != null) {
            sb.append("현재 ").append(shortOrFull(current)).append("에 재직 중");
            if (current.getStartDate() != null) {
                sb.append(" (입사 ").append(current.getStartDate().format(YM)).append(")");
            }
            sb.append(".\n");
        } else {
            sb.append("현재 재직 중인 회사 없음.");
            CareerCompany last = companies.stream()
                    .filter(c -> c.getEndDate() != null)
                    .reduce((a, b) -> a.getEndDate().isAfter(b.getEndDate()) ? a : b)
                    .orElse(null);
            if (last != null) {
                sb.append(" ").append(shortOrFull(last)).append(" 퇴사(")
                        .append(last.getEndDate().format(YM)).append(") 이후 이직 준비 중.");
            }
            sb.append("\n");
        }
        if (dp != null && notBlank(dp.getAvailability())) {
            sb.append("구직 상태: ").append(dp.getAvailability().trim()).append("\n");
        }
        if (dp != null && notBlank(dp.getJobSearchNote())) {
            sb.append("다음 계획: ").append(dp.getJobSearchNote().trim()).append("\n");
        }
        sb.append("\n");

        // ── 회사별 상세 (최신순) ──
        for (int i = companies.size() - 1; i >= 0; i--) {
            CareerCompany c = companies.get(i);
            sb.append("### ").append(c.getCompanyName()).append("\n");
            sb.append("기간: ").append(c.getPeriodLabel());
            if (Boolean.TRUE.equals(c.getIsCurrent())) {
                sb.append(" (재직 중)");
            } else if (c.getEndDate() != null) {
                sb.append(" (퇴사)");
            }
            sb.append("\n");
            if (notBlank(c.getRole())) sb.append("직무: ").append(c.getRole().trim()).append("\n");
            if (notBlank(c.getDomain())) sb.append("도메인: ").append(c.getDomain().trim()).append("\n");
            if (notBlank(c.getStack())) sb.append("스택: ").append(c.getStack().trim()).append("\n");
            if (notBlank(c.getLeaveReason())) {
                sb.append("퇴사/이직 사유: ").append(c.getLeaveReason().trim()).append("\n");
            }

            List<CareerProject> projects = allProjects.stream()
                    .filter(p -> p.getCompanyId() != null && p.getCompanyId().equals(c.getId()))
                    .toList();
            if (!projects.isEmpty()) {
                boolean isCurrentCompany = Boolean.TRUE.equals(c.getIsCurrent());
                sb.append("프로젝트:\n");
                for (CareerProject p : projects) {
                    sb.append("- ").append(p.getTitle());
                    if (notBlank(p.getPeriodLabel())) sb.append(" (").append(p.getPeriodLabel().trim()).append(")");
                    sb.append("\n");
                    // 퇴사한 회사의 프로젝트 설명은 재직 당시 현재형이라 재직 여부 판단을 흐린다 → 제목만.
                    // 현재 회사만 설명을 붙인다(길면 앞 200자).
                    if (isCurrentCompany) {
                        String desc = plainText(p.getParagraphs());
                        if (!desc.isEmpty()) {
                            sb.append("  ").append(desc.length() > 200 ? desc.substring(0, 200) + "…" : desc).append("\n");
                        }
                    }
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String shortOrFull(CareerCompany c) {
        return notBlank(c.getShortName()) ? c.getShortName().trim() : c.getCompanyName();
    }

    /** 리치텍스트(HTML) 를 한 줄 평문으로. 태그 제거 + 공백 정리. */
    private String plainText(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 경력(회사) — 경력 타임라인(git log)과 경력 기술서에서 함께 사용.
 */
@Entity
@Table(name = "career_company")
@Getter
@Setter
@NoArgsConstructor
public class CareerCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "domain")
    private String domain; // 경력기술서 좌측 메뉴 그룹핑 기준 (예: "음성인식", "보안(SIEM)")

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "short_name")
    private String shortName; // whoami 카드 등 좁은 자리에 쓰는 짧은 표기명 (예: "TNS Soft")

    @Column(name = "period_label", nullable = false)
    private String periodLabel; // 사람이 읽는 기간 문구 (예: "2024.04 ~ 재직중 · 2년 5개월")

    @Column(name = "start_date")
    private LocalDate startDate; // 총 경력 연차 계산용 입사일 (매월 1일로 저장)

    @Column(name = "end_date")
    private LocalDate endDate; // 총 경력 연차 계산용 퇴사일 (재직중이면 NULL)

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "commit_tag")
    private String commitTag;

    @Column(name = "stack", columnDefinition = "TEXT")
    private String stack; // 쉼표(,)로 구분된 기술 스택 목록

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

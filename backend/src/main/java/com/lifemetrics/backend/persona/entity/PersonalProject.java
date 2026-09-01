package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 포트폴리오 "개인 프로젝트"(05 섹션) 항목.
 * kind=FEATURED 는 대표 프로젝트(기능 카드 포함), kind=MINI 는 작은 프로젝트 카드.
 */
@Entity
@Table(name = "personal_project")
@Getter
@Setter
@NoArgsConstructor
public class PersonalProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kind", nullable = false)
    private String kind; // FEATURED | MINI

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "blurb", columnDefinition = "TEXT")
    private String blurb; // 프로젝트 설명

    @Column(name = "repo_url")
    private String repoUrl; // "GitHub에서 보기" 링크 (비면 profile.contact.github 사용)

    @Column(name = "period_label")
    private String periodLabel; // MINI 카드용 (예: "대학원 논문 주제 · 2016 ~ 2018")

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // 쉼표(,)로 구분된 태그 목록 (MINI 카드용)

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

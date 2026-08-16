package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 경력 기술서의 프로젝트 항목 — CareerCompany 에 귀속.
 */
@Entity
@Table(name = "career_project")
@Getter
@Setter
@NoArgsConstructor
public class CareerProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "period_label")
    private String periodLabel;

    @Column(name = "paragraphs", columnDefinition = "LONGTEXT", nullable = false)
    private String paragraphs; // 빈 줄(\n\n)로 구분된 설명 문단 목록

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

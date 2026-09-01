package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 포트폴리오 05 섹션의 "기술 선택 이유" 목록 — 카테고리별 (키 : 설명) 쌍.
 */
@Entity
@Table(name = "portfolio_dependency")
@Getter
@Setter
@NoArgsConstructor
public class PortfolioDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", nullable = false)
    private String category; // 그룹 헤더 (예: "backend — package.json", "ai-integration", "infra")

    @Column(name = "dep_key", nullable = false)
    private String depKey; // 따옴표 안에 표시되는 키 (예: "spring-boot + jpa")

    @Column(name = "note", columnDefinition = "TEXT")
    private String note; // 선택 이유 설명

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

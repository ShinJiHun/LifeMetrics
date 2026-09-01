package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 포트폴리오 05 섹션의 트러블슈팅(디버깅) 로그 — diff 형태로 "증상 → 조치"를 보여준다.
 */
@Entity
@Table(name = "portfolio_troubleshoot")
@Getter
@Setter
@NoArgsConstructor
public class PortfolioTroubleshoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ref_label")
    private String refLabel; // 커밋 해시 느낌의 라벨 (예: "#42")

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "removed_lines", columnDefinition = "TEXT")
    private String removedLines; // 줄바꿈(\n)으로 구분 — diff '-' 라인 (증상/원인)

    @Column(name = "added_lines", columnDefinition = "TEXT")
    private String addedLines; // 줄바꿈(\n)으로 구분 — diff '+' 라인 (조치/교훈)

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

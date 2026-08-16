package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 포트폴리오 소개 섹션 — 소제목 + 문단 목록.
 */
@Entity
@Table(name = "profile_intro_section")
@Getter
@Setter
@NoArgsConstructor
public class ProfileIntroSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subtitle", nullable = false)
    private String subtitle;

    @Column(name = "lines", columnDefinition = "TEXT", nullable = false)
    private String lines; // 줄바꿈(\n)으로 구분된 문단 목록

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

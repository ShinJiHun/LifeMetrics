package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 대표 개인 프로젝트(PersonalProject kind=FEATURED)의 기능 카드.
 */
@Entity
@Table(name = "personal_project_feature")
@Getter
@Setter
@NoArgsConstructor
public class PersonalProjectFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "icon")
    private String icon; // 이모지 1개 (예: "📐")

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // 쉼표(,)로 구분된 태그 목록

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

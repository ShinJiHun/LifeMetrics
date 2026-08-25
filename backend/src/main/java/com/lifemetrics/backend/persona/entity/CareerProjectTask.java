package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 경력 기술서 프로젝트 안의 개별 업무 항목 — CareerProject 에 귀속.
 */
@Entity
@Table(name = "career_project_task")
@Getter
@Setter
@NoArgsConstructor
public class CareerProjectTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

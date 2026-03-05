package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "muscle_group")
@Getter
public class MuscleGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_ko")
    private String nameKo;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "body_part")
    @Enumerated(EnumType.STRING)
    private BodyPart bodyPart;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public enum BodyPart {
        UPPER, LOWER, CORE
    }
}
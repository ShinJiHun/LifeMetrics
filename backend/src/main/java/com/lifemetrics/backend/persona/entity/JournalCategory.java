package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 대메뉴 — 예: 자바의 정석 / 회사 일기. persona 별로 구분. */
@Entity
@Table(name = "journal_category")
@Getter
@Setter
@NoArgsConstructor
public class JournalCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "persona", nullable = false, length = 20)
    private String persona;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}

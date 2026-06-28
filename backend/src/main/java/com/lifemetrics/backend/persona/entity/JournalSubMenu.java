package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 소메뉴 — 예: 1단원 / 26년. 대메뉴(JournalCategory)에 귀속. */
@Entity
@Table(name = "journal_sub_menu")
@Getter
@Setter
@NoArgsConstructor
public class JournalSubMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}

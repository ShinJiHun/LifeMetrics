package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 글 — 소메뉴(JournalSubMenu)에 귀속. visibility = public | private. */
@Entity
@Table(name = "journal_post")
@Getter
@Setter
@NoArgsConstructor
public class JournalPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sub_id", nullable = false)
    private Long subId;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility = "public";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        // 마이그레이션 등에서 원본 작성일을 미리 세팅한 경우 보존한다.
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

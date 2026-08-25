package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 경력 업무 항목에 첨부된 화면 이미지/GIF/영상 — CareerProjectTask 에 귀속.
 * 실제 파일은 career.media.path 디렉터리에 저장되고, filename 으로 서빙한다.
 */
@Entity
@Table(name = "career_task_media")
@Getter
@Setter
@NoArgsConstructor
public class CareerTaskMedia {

    public enum MediaKind {
        IMAGE, VIDEO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "filename", nullable = false)
    private String filename; // 디스크에 저장된 이름 (UUID 기반, 확장자 포함)

    @Enumerated(EnumType.STRING)
    @Column(name = "media_kind", nullable = false, length = 20)
    private MediaKind mediaKind;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

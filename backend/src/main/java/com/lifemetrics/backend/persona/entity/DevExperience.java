package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 개발자 페르소나 경험 카드: 한 사건 = 카드 하나. 방문자 챗에는 PUBLIC 카드만 쓴다. */
@Entity
@Table(name = "dev_experience")
@Getter
@Setter
@NoArgsConstructor
public class DevExperience {

    public static final String PUBLIC = "PUBLIC";
    public static final String PRIVATE = "PRIVATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String project;

    @Column(name = "happened_on")
    private LocalDate happenedOn;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String situation;

    @Column(columnDefinition = "TEXT")
    private String cause;

    @Column(columnDefinition = "TEXT")
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String lesson;

    private String tags;

    @Column(nullable = false)
    private String visibility = PRIVATE;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** 검색·프롬프트에 넣는 한 덩어리 텍스트. */
    public String asText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(project).append(happenedOn == null ? "" : " " + happenedOn).append("] ").append(title).append('\n');
        line(sb, "상황", situation);
        line(sb, "원인", cause);
        line(sb, "결정", decision);
        line(sb, "결과", result);
        line(sb, "배운 점", lesson);
        line(sb, "태그", tags);
        return sb.toString().strip();
    }

    private static void line(StringBuilder sb, String label, String v) {
        if (v != null && !v.isBlank()) sb.append(label).append(": ").append(v.strip()).append('\n');
    }
}

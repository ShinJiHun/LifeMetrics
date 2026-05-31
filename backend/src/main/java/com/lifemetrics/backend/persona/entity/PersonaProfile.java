package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 블로그 글 전체를 LLM으로 1회 요약해 만든 페르소나 프로필 캐시.
 * 글 갱신(refresh) 시 재생성한다. 최신 1건만 유효하게 사용한다.
 */
@Entity
@Table(name = "persona_profile")
@Getter
@Setter
@NoArgsConstructor
public class PersonaProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_text", columnDefinition = "LONGTEXT", nullable = false)
    private String profileText;

    @Column(name = "post_count")
    private Integer postCount;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }
}
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 애슐리트 챗 대화 한 건. 메시지는 AthleteChatMessage. */
@Entity
@Table(name = "athlete_chat_session")
@Getter
@Setter
@NoArgsConstructor
public class AthleteChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    private String mode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 메시지를 저장할 때마다 서비스가 갱신한다(목록 정렬용). */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

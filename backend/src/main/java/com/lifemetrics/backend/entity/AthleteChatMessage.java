package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "athlete_chat_message")
@Getter
@Setter
@NoArgsConstructor
public class AthleteChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** user | assistant */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String content;

    /** 답변 근거로 호출한 도구 JSON 배열. 사용자 메시지·context 모드는 null */
    @Column(name = "tool_calls", columnDefinition = "json")
    private String toolCalls;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}

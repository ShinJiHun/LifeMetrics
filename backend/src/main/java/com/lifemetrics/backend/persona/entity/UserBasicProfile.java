package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_basic_profile")
@Getter
@Setter
public class UserBasicProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Lob
    private String career;    // JSON 배열 문자열

    @Lob
    private String education;

    @Lob
    private String intro;

    @Lob
    private String contact;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
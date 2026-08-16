package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 개발자 포트폴리오 소개 문구 + 연락처. 단일 행만 사용한다.
 */
@Entity
@Table(name = "developer_profile")
@Getter
@Setter
@NoArgsConstructor
public class DeveloperProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elevator_pitch", columnDefinition = "TEXT", nullable = false)
    private String elevatorPitch;

    @Column(name = "highlights", columnDefinition = "TEXT")
    private String highlights; // 줄바꿈(\n)으로 구분된 핵심 성과 목록

    @Column(name = "phone")
    private String phone;

    @Column(name = "github")
    private String github;

    @Column(name = "blog")
    private String blog;
}

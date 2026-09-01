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

    @Column(name = "headline", columnDefinition = "TEXT")
    private String headline; // 포트폴리오 히어로 제목. 줄바꿈(\n)으로 개행, {{강조문구}}로 accent 색 강조

    @Column(name = "subheadline", columnDefinition = "TEXT")
    private String subheadline; // 포트폴리오 히어로 부제

    @Column(name = "role_tagline")
    private String roleTagline; // whoami 카드/푸터에 쓰이는 직함 태그라인 (예: "Backend Developer")

    @Column(name = "focus_tags", columnDefinition = "TEXT")
    private String focusTags; // 쉼표(,)로 구분된 전문분야 태그 목록

    @Column(name = "contact_blurb", columnDefinition = "TEXT")
    private String contactBlurb; // 연락처 섹션 안내 문구

    @Column(name = "side_project")
    private String sideProject; // whoami 카드 sideProject 값 (예: "LifeMetrics")

    @Column(name = "availability")
    private String availability; // 구직/이직 준비 중일 때 표시할 문구 (예: "이직 준비 중")

    @Column(name = "open_to_work")
    private Boolean openToWork; // 구직/이직 준비 여부

    @Column(name = "job_search_note", columnDefinition = "TEXT")
    private String jobSearchNote; // 현재 구직 상황·다음 계획 서술. 페르소나 챗 컨텍스트용(포트폴리오 비노출).

    @Column(name = "phone")
    private String phone;

    @Column(name = "github")
    private String github;

    @Column(name = "blog")
    private String blog;
}

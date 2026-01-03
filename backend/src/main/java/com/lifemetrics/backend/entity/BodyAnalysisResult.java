package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "body_analysis_result")
@Getter
@Setter
public class BodyAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;   // ✅ 반드시 필요

    private Long userId;

    private String model;          // gpt-4.1, gemini-1.5, claude-3
    private String promptVersion;  // v1, v2 등

    @Lob
    @Column(columnDefinition = "TEXT")
    private String analysisText;

    private LocalDate targetDate;  // 분석 대상 날짜

    private LocalDateTime createdAt;
}

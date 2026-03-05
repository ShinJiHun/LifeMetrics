package com.lifemetrics.backend.entity;

import com.lifemetrics.backend.dto.AiAnalysisResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "body_analysis_summary_state")
@Getter
@Setter
public class BodyAnalysisSummaryState {

    @Id
    private Long userId;

    @Column(columnDefinition = "longtext")
    private String summaryJson;

    private String version;

    private LocalDateTime updatedAt;

    /* ===============================
       🔥 static factory (비어있는 서사)
       =============================== */
    public static BodyAnalysisSummaryState empty(Long userId) {
        BodyAnalysisSummaryState s = new BodyAnalysisSummaryState();
        s.userId = userId;
        s.version = "v1";
        s.summaryJson = "{}";   // 최초는 빈 JSON
        s.updatedAt = LocalDateTime.now();
        return s;
    }

    /* ===============================
       🔥 누적 서사 업데이트
       =============================== */
    public void updateFrom(AiAnalysisResponse response) {
        // 지금은 AI가 준 summary를 그대로 저장
        // (나중에 merge 전략 고도화 가능)
        this.summaryJson = response.summaryJson();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEmpty() {
        return summaryJson.isEmpty();
    }
}

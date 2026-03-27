package com.lifemetrics.backend.entity;

//import com.lifemetrics.backend.Client.AiProvider;
import com.lifemetrics.backend.client.AiProvider;
import com.lifemetrics.backend.dto.AiAnalysisResponse;
import com.lifemetrics.backend.domain.BodyType;
import com.lifemetrics.backend.domain.GoalType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "body_analysis_result")
@Getter
@Setter
public class BodyAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 인바디 레코드 FK
    @Column(name = "body_record_id", nullable = false)
    private Long bodyRecordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalType goalType;

    @Column(nullable = false)
    private String modelProvider;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private BodyType bodyType;   // ✅ 추가

    private String analysisVersion = "v1";

    @Column(columnDefinition = "longtext")
    private String analysisJson;

    @Column(columnDefinition = "text")
    private String analysisText;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static BodyAnalysisResult from(
            UserBodyRecord curr,
            GoalType goalType,
            AiProvider provider,
            AiAnalysisResponse response
    ) {
        BodyAnalysisResult r = new BodyAnalysisResult();
        r.setBodyRecordId(curr.getId());
        r.setGoalType(goalType);
        r.setModelProvider(provider.name());
        r.setModelName(response.model());           // ✅ 수정
        r.setBodyType(response.bodyType());
        r.setAnalysisJson(response.summaryJson());
        r.setAnalysisText(response.summaryJson());  // TODO: 별도 텍스트 필드 필요시 수정
        return r;
    }
}

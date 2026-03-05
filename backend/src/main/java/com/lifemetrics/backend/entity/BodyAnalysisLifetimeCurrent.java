package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "body_analysis_lifetime_current")
@IdClass(BodyAnalysisLifetimeCurrentId.class)
@Getter @Setter
public class BodyAnalysisLifetimeCurrent {

    @Id
    private Long userId;

    @Id
    private String goalType;   // DIET / MAINTAIN / BULK

    private Long snapshotId;   // body_analysis_result.id

    private String modelProvider;
    private String modelName;

    private LocalDateTime updatedAt;
}

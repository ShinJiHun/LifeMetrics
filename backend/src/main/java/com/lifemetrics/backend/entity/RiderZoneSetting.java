package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 라이더 존 기준값. 파워존(FTP 기준)·심박존(최대심박 기준) 계산과 AI 분석에 쓴다.
 */
@Entity
@Table(name = "rider_zone_setting")
@Getter
@Setter
@NoArgsConstructor
public class RiderZoneSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private Integer ftp;

    @Column(name = "max_hr")
    private Integer maxHr;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}

package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 랜도너스 공식 참가 기록(브레베·퍼머넌트·플레쉬). 완주/DNF 와 코스 거리를 담는다.
 * activity_core 는 GPS 가 있는 라이딩만이라 경력의 기준은 이 테이블이다.
 */
@Entity
@Table(name = "randonneuring_result")
@Getter
@Setter
@NoArgsConstructor
public class RandonneuringResult {

    public static final String FINISHED = "FINISHED";
    public static final String DNF = "DNF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    private String organizer;

    /** BREVET | PERMANENT | FLECHE | POPULAIRE */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "permanent_no")
    private String permanentNo;

    /** 공식 코스 거리 (DNF 도 코스 거리). 모르면 null */
    @Column(name = "distance_km")
    private Integer distanceKm;

    /** FINISHED | DNF */
    @Column(nullable = false)
    private String status;

    /** 완주 시간(초). DNF 는 null */
    @Column(name = "riding_time_sec")
    private Integer ridingTimeSec;

    /** 원본 id (kora_source_id 또는 ebr:<entry_id>) — 다시 가져올 때 중복 판단 */
    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "homologation_no")
    private String homologationNo;

    @Column(name = "activity_core_id")
    private Long activityCoreId;

    private String notes;
}

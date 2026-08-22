// entity/ActivityCore.java
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_core")
@Getter
public class ActivityCore {
    @Id
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String filename;

    // ★ 라이드 제목 (편집 가능 → Setter 추가)
    @Setter
    private String name;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    // ★ 활동 병합(mergeActivities) 시 target의 end_time으로 갱신 → Setter 추가
    @Setter
    @Column(name = "end_time")
    private LocalDateTime endTime;

    // ★ 활동 병합 시 target 수치를 합산 → Setter 추가
    @Setter
    @Column(name = "total_distance")
    private Double totalDistance;

    @Setter
    @Column(name = "moving_time")
    private Integer movingTime;

    @Setter
    @Column(name = "elapsed_time")
    private Integer elapsedTime;

    @Setter
    @Column(name = "total_ascent")
    private Double totalAscent;

    @Setter
    @Column(name = "total_descent")
    private Double totalDescent;

    @Column(name = "avg_speed")
    private Double avgSpeed;

    @Setter
    @Column(name = "max_speed")
    private Double maxSpeed;

    @Column(name = "avg_cadence")
    private Double avgCadence;

    // ★ 최대 케이던스 (Avg/Max 테이블용)
    @Column(name = "max_cadence")
    private Double maxCadence;

    @Column(name = "avg_heart_rate")
    private Double avgHeartRate;

    @Column(name = "max_heart_rate")
    private Double maxHeartRate;

    @Column(name = "avg_power")
    private Double avgPower;

    @Setter
    @Column(name = "max_power")
    private Double maxPower;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lon")
    private Double startLon;

    // ★ 활동 병합 시 target의 종료 좌표로 갱신 → Setter 추가
    @Setter
    @Column(name = "end_lat")
    private Double endLat;

    @Setter
    @Column(name = "end_lon")
    private Double endLon;

    // ★ 활동 병합 시 합쳐진 포인트로 재인코딩 → Setter 추가
    @Setter
    @Column(columnDefinition = "longtext")
    private String polyline;

    // ★ 자동매칭된 자전거 (bike 테이블 FK)
    // ★ 활동 병합 시 parent에 자전거 매칭이 없으면 target 것을 이어받음 → Setter 추가
    @Setter
    @Column(name = "bike_id")
    private Long bikeId;

    @Column(name = "uphill_distance")
    private Double uphillDistance;

    @Column(name = "flat_distance")
    private Double flatDistance;

    @Column(name = "down_distance")
    private Double downDistance;

    @Setter
    @Column(name = "calories")
    private int calories;

    @Column(name = "has_power")
    private Boolean hasPower;

    @Column(name = "normalized_power")
    private Double normalizedPower;

    @Column(name = "left_right_balance")
    private Double leftRightBalance;

    @Column(name = "power_source")
    private String powerSource;

    // ★ 출발지명 캐시 (Setter는 mapper에서 호출)
    @Setter
    @Column(name = "location_name")
    private String locationName;

    // ★ Strava 스타일 RE 점수 캐시 (Setter는 mapper에서 호출)
    @Setter
    @Column(name = "relative_effort")
    private Integer relativeEffort;
}

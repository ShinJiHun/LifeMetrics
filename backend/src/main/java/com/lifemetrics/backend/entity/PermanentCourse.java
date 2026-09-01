// entity/PermanentCourse.java
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permanent_courses")
@Getter
public class PermanentCourse {

    @Id
    @Column(name = "permanent_no")
    private String permanentNo;

    @Column(name = "ebrevet_id")
    private Long ebrevetId;

    private String name;

    @Column(name = "distance_km")
    private Integer distanceKm;

    @Column(name = "time_limit_min")
    private Integer timeLimitMin;

    @Column(name = "time_limit_hm")
    private String timeLimitHm;

    @Column(name = "elevation_gain_m")
    private Integer elevationGainM;

    private String region;

    @Column(name = "is_loop")
    private Boolean isLoop;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "note_url")
    private String noteUrl;

    // ★ NAS의 gpx(대표 파일 1개)를 파싱해 인코딩한 경로 — 코스 확인 페이지 지도 표시용
    // (PermanentGpxService.refreshPolyline로 채움 → Setter 추가)
    @Setter
    @Column(columnDefinition = "longtext")
    private String polyline;
}

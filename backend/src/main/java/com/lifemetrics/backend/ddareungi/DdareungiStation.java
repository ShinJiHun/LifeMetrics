package com.lifemetrics.backend.ddareungi;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 따릉이 대여소 좌표 캐시 (서울 열린데이터 bikeList 동기화). */
@Entity
@Table(name = "ddareungi_station")
@Getter
@Setter
@NoArgsConstructor
public class DdareungiStation {

    @Id
    @Column(name = "station_id")
    private String stationId;

    @Column(name = "station_no")
    private String stationNo;

    @Column(name = "station_name")
    private String stationName;

    private Double lat;
    private Double lon;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

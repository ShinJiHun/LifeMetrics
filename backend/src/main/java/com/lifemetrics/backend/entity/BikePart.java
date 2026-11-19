package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 자전거 소모품 한 개(체인·타이어 등)의 장착 기간. 교체하면 removedOn 을 채우고 새 행을 만든다. */
@Entity
@Table(name = "bike_part")
@Getter
@Setter
public class BikePart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bike_id", nullable = false)
    private Long bikeId;

    @Column(name = "part_type", nullable = false, length = 32)
    private String partType;

    private String name;

    @Column(name = "installed_on", nullable = false)
    private LocalDate installedOn;

    @Column(name = "removed_on")
    private LocalDate removedOn;

    @Column(name = "initial_km", nullable = false)
    private Double initialKm = 0.0;

    @Column(name = "interval_km")
    private Integer intervalKm;

    @Column(name = "interval_months")
    private Integer intervalMonths;

    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

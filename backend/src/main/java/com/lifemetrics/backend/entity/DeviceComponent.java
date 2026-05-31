package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_component")
@Getter
@Setter
public class DeviceComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    // CHAINRING / CASSETTE / TIRE / CHAIN / WHEEL ...
    @Column(name = "component_type", nullable = false, length = 32)
    private String componentType;

    @Column(nullable = false, length = 64)
    private String spec;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

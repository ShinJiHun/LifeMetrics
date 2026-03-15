package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_info")
@Getter
@Setter
public class DeviceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String manufacturer;
    private String model;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "device_type")
    private String deviceType;   // HEAD_UNIT, SPEED_SENSOR, CADENCE_SENSOR, HEART_RATE, POWER_METER

    @Column(name = "user_label")
    private String userLabel;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

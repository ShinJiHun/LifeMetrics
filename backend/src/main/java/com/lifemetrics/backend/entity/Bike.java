package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bike")
@Data
public class Bike {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String brand;
    private String model;
    private Integer modelYear;
    private String bikeType;
    private String rideCategory;
    private String frameSize;
    private String frameMaterial;
    private Double weightKg;
    private String wheelSize;
    private String groupset;
    private String shiftingType;
    private Integer speeds;
    private Integer chainringOuter;
    private Integer chainringInner;
    private String cassetteLabel;

    private LocalDate purchaseDate;
    private Integer purchasePrice;

    private Double totalDistance = 0.0;
    private Integer totalTime = 0;

    private String photoUrl;

    @Column(name = "is_retired")
    private Boolean isRetired = false;
    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
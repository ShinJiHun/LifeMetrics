package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_weather_point")
@Getter
public class ActivityWeatherPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_core_id")
    private Long activityCoreId;

    private Integer seq;

    @Column(name = "point_time")
    private LocalDateTime pointTime;

    private Double lat;
    private Double lon;

    private Double temperature;
    private Double humidity;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "wind_deg")
    private Double windDeg;

    private Double pressure;

    @Column(name = "weather_code")
    private Integer weatherCode;

    @Column(name = "weather_desc")
    private String weatherDesc;
}
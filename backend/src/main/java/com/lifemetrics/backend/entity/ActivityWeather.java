// entity/ActivityWeather.java
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activity_weather")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityWeather {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_core_id")
    private Long activityCoreId;

    private Double temperature;
    private Double humidity;

    @Column(name = "wind_speed")
    private Double windSpeed;

    private Double pressure;

    @Column(name = "wind_deg")
    private Integer windDeg;
}
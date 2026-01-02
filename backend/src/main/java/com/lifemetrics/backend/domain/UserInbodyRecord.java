package com.lifemetrics.backend.domain;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

import java.time.LocalDate;

@Entity
@Table(name = "user_inbody_record")
@Getter
@Setter
public class UserInbodyRecord {

    @Id
    @GeneratedValue
    private Long id;

    private Long userId;
    private LocalDate recordDate;

    private Double weight;
    private Double skeletalMuscleMass;
    private Double bodyFatPercentage;
    private Double bodyFatMass;

    @Transient
    private Double weightDelta;
    @Transient
    private Double skeletalMuscleMassDelta;
    @Transient
    private Double bodyFatPercentageDelta;

    public void calculateDelta(UserInbodyRecord prev) {
        this.weightDelta = calc(this.weight, prev.weight);
        this.skeletalMuscleMassDelta = calc(this.skeletalMuscleMass, prev.skeletalMuscleMass);
        this.bodyFatPercentageDelta = calc(this.bodyFatPercentage, prev.bodyFatPercentage);
    }

    private Double calc(Double a, Double b) {
        return (a != null && b != null) ? a - b : null;
    }
}

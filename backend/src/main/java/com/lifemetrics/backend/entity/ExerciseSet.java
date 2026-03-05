
// entity/ExerciseSet.java
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exercise_set")
@Getter
@Setter
public class ExerciseSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_id")
    private Long logId;

    @Column(name = "set_number")
    private Integer setNumber;

    private BigDecimal weight;
    private Integer reps;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
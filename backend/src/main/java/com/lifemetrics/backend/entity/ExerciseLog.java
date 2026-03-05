
// entity/ExerciseLog.java
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exercise_log")
@Getter
@Setter
public class ExerciseLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "exercise_item_id")
    private Long exerciseItemId;

    private BigDecimal weight;
    private Integer reps;
    private Integer sets;

    @Column(name = "duration_sec")
    private Integer durationSec;

    private Integer rpe;
    private String memo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

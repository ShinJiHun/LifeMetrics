package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

// entity/ExerciseCategory.java
@Entity
@Table(name = "exercise_category")
@Getter
public class ExerciseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
}

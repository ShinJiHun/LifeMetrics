package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "exercise_muscle_map")
@Getter
public class ExerciseMuscleMap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exercise_item_id")
    private Long exerciseItemId;

    @Column(name = "muscle_group_id")
    private Long muscleGroupId;

    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "activation_level")
    private Integer activationLevel;

    public enum Role {
        PRIMARY, SECONDARY, SYNERGIST
    }
}
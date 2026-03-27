package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "exercise_item")
@Getter
public class ExerciseItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "name_ko")
    private String nameKo;

    @Column(name = "name_en")
    private String nameEn;

    private String description;

    @Column(name = "equipment_type")
    private String equipmentType;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "gif_url")
    private String gifUrl;

    @Column(name = "youtube_url")
    private String youtubeUrl;

    @Column(name = "is_active")
    private Boolean isActive;
}

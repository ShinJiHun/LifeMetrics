package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 활동 사진. 지금은 블로그(NAS persona/blog) 후기 사진을 data_pipeline/persona/blog_photo_link.py 가 채운다.
 * image_path / thumb_path 는 블로그 보관 폴더(persona.blog.archive-path) 기준 상대 경로.
 * location_source: EXIF_TIME(촬영 시각의 라이딩 좌표) / ORDER_ESTIMATE(앞뒤 사진 시각으로 추정) / NONE(위치 없음)
 */
@Entity
@Table(name = "activity_image")
@Getter
@NoArgsConstructor
public class ActivityImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_core_id", nullable = false)
    private Long activityCoreId;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "thumb_path")
    private String thumbPath;

    private String source;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    private Double lat;
    private Double lon;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "location_source")
    private String locationSource;

    private String caption;
    private Integer width;
    private Integer height;

    @Column(name = "blog_post_no")
    private Integer blogPostNo;

    @Column(name = "blog_post_title")
    private String blogPostTitle;

    @Column(name = "blog_post_url")
    private String blogPostUrl;
}

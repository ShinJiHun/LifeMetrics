package com.lifemetrics.backend.persona.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "blog_post")
@Getter
@Setter
@NoArgsConstructor
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, unique = true, length = 512)
    private String url;

    @Column(name = "post_no")
    private Integer postNo;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "author", length = 100)
    private String author;

    @Column(name = "categories", length = 512)
    private String categories;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "crawled_at")
    private LocalDateTime crawledAt;

    @PrePersist
    protected void onCreate() {
        crawledAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        crawledAt = LocalDateTime.now();
    }
}
package com.lifemetrics.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** RAG 조각: 원본 텍스트 일부 + (임베딩을 켰을 때) 정규화된 임베딩(float32 리틀엔디언). 끄면 키워드 검색만 한다. */
@Entity
@Table(name = "note_chunk")
@Getter
@Setter
@NoArgsConstructor
public class NoteChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "chunk_no", nullable = false)
    private Integer chunkNo;

    @Column(name = "chunk_date")
    private LocalDate chunkDate;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String text;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    /** 임베딩 모델. 키워드 전용이면 null */
    private String model;

    private Integer dims;

    @Lob
    @Column(columnDefinition = "mediumblob")
    private byte[] embedding;
}

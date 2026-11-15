package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.NoteChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteChunkRepository extends JpaRepository<NoteChunk, Long> {

    List<NoteChunk> findByUserId(Long userId);
}

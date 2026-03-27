package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ExerciseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseItemRepository extends JpaRepository<ExerciseItem, Long> {

    List<ExerciseItem> findByCategoryIdAndIsActiveTrue(Long categoryId);

    List<ExerciseItem> findByIsActiveTrue();

    List<ExerciseItem> findByGifUrlIsNotNullAndIsActiveTrue();
}

// repository/ExerciseItemRepository.java
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ExerciseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExerciseItemRepository extends JpaRepository<ExerciseItem, Long> {
    List<ExerciseItem> findByCategoryId(Long categoryId);
    List<ExerciseItem> findByMediaUrlIsNotNull();
}
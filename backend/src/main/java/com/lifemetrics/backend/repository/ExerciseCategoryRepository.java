package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ExerciseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseCategoryRepository extends JpaRepository<ExerciseCategory, Long> {
}

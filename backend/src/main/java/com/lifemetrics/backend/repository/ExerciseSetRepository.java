// ExerciseSetRepository.java
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ExerciseSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {
    List<ExerciseSet> findByLogIdOrderBySetNumber(Long logId);
}
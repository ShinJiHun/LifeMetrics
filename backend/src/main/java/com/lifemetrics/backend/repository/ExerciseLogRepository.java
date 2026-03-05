// ExerciseLogRepository.java
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ExerciseLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExerciseLogRepository extends JpaRepository<ExerciseLog, Long> {
    List<ExerciseLog> findBySessionId(Long sessionId);


}
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.BodyGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BodyGoalRepository extends JpaRepository<BodyGoal, Long> {

    List<BodyGoal> findByUserIdOrderByStartDateDescIdDesc(Long userId);

    Optional<BodyGoal> findTopByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    List<BodyGoal> findByUserIdAndStatus(Long userId, String status);
}

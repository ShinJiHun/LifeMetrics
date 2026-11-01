package com.lifemetrics.backend.health.repository;

import com.lifemetrics.backend.health.entity.FoodLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {

    List<FoodLog> findByUserIdAndLogDateOrderByEatTimeAscIdAsc(Long userId, LocalDate logDate);
}

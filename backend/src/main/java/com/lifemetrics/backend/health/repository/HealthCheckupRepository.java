package com.lifemetrics.backend.health.repository;

import com.lifemetrics.backend.health.entity.HealthCheckup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HealthCheckupRepository extends JpaRepository<HealthCheckup, Long> {
    List<HealthCheckup> findByUserIdOrderByCheckupDateDesc(Long userId);
}

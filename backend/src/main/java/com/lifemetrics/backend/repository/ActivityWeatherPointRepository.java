package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ActivityWeatherPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityWeatherPointRepository extends JpaRepository<ActivityWeatherPoint, Long> {
    List<ActivityWeatherPoint> findByActivityCoreIdOrderBySeq(Long activityCoreId);
}
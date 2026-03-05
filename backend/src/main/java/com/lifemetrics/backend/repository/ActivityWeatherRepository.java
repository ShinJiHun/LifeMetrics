// repository/ActivityWeatherRepository.java
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ActivityWeather;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ActivityWeatherRepository extends JpaRepository<ActivityWeather, Long> {
    Optional<ActivityWeather> findByActivityCoreId(Long activityCoreId);
}
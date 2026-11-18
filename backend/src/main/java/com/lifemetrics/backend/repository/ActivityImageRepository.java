package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ActivityImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityImageRepository extends JpaRepository<ActivityImage, Long> {
    List<ActivityImage> findByActivityCoreIdOrderBySortOrderAscIdAsc(Long activityCoreId);
}

// repository/ActivityPointRepository.java
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ActivityPoint;
import com.lifemetrics.backend.entity.ActivityPointId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityPointRepository extends JpaRepository<ActivityPoint, ActivityPointId> {
    List<ActivityPoint> findByActivityCoreIdOrderBySeqAsc(Long activityCoreId);
}
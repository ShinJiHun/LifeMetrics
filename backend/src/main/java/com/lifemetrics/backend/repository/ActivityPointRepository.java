// repository/ActivityPointRepository.java
package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.ActivityPoint;
import com.lifemetrics.backend.entity.ActivityPointId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * ActivityPoint는 (activityCoreId, seq) 복합키 엔티티입니다.
 * <p>
 * Relative Effort 계산을 위해 특정 라이딩의 모든 포인트를 seq 오름차순으로
 * 가져오는 메서드를 추가합니다.
 */
public interface ActivityPointRepository
        extends JpaRepository<ActivityPoint, ActivityPointId> {

    /**
     * 특정 라이딩의 모든 포인트를 seq 오름차순으로 조회.
     * <p>
     * 600km 라이딩 같은 초장거리는 포인트가 수만 개 단위로 쌓일 수 있으니,
     * RE 계산 외 다른 용도로 호출할 땐 주의하세요.
     */
    List<ActivityPoint> findByActivityCoreIdOrderBySeqAsc(Long activityCoreId);

    /**
     * 심박이 있는 포인트만 (RE 계산 시 페이로드 절감용).
     * 필요할 때만 사용.
     */
    List<ActivityPoint> findByActivityCoreIdAndHeartRateNotNullOrderBySeqAsc(Long activityCoreId);
}

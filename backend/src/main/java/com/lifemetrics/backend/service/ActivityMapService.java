package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.ActivityMapDto;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.ActivityMapRow;
import com.lifemetrics.backend.repository.ActivityPolylineRow;
import com.lifemetrics.backend.util.PolylineEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 라이딩 지도(전체 기록 한 지도에) 데이터.
 *
 * 원본 polyline 은 GPS 포인트를 전부 담고 있어 400km 한 건이 ~140KB 라 전체를 그대로 내려보내기엔 크다.
 * 약 10m 오차로 단순화해 메모리에 캐시하고, 캐시에 없는 기록만 DB 에서 polyline 을 읽는다.
 * 병합으로 경로가 바뀌면 거리도 바뀌므로 (id, 거리) 를 캐시 키로 써서 자동으로 다시 계산되게 한다.
 */
@Service
@RequiredArgsConstructor
public class ActivityMapService {

    /** 약 10m (위도 1도 ≈ 111km) */
    private static final double SIMPLIFY_TOLERANCE_DEG = 0.00009;

    private final ActivityCoreRepository coreRepository;

    private final Map<String, String> simplifiedCache = new ConcurrentHashMap<>();

    public List<ActivityMapDto> getMapActivities(Long userId) {
        List<ActivityMapRow> rows = coreRepository.findMapRowsByUserId(userId);

        List<Long> missing = rows.stream()
                .filter(r -> !simplifiedCache.containsKey(cacheKey(r)))
                .map(ActivityMapRow::getId)
                .toList();
        if (!missing.isEmpty()) {
            Map<Long, ActivityMapRow> byId = rows.stream()
                    .collect(java.util.stream.Collectors.toMap(ActivityMapRow::getId, r -> r));
            // 한 번에 너무 많은 longtext 를 읽지 않도록 나눠서
            for (int i = 0; i < missing.size(); i += 50) {
                for (ActivityPolylineRow p : coreRepository.findPolylinesByIdIn(missing.subList(i, Math.min(i + 50, missing.size())))) {
                    simplifiedCache.put(cacheKey(byId.get(p.getId())), simplify(p.getPolyline()));
                }
            }
        }

        return rows.stream()
                .map(r -> new ActivityMapDto(
                        r.getId(), r.getName(), r.getStartTime(), r.getRideType(),
                        r.getTotalDistance(), r.getTotalAscent(), r.getMovingTime(),
                        simplifiedCache.getOrDefault(cacheKey(r), "")))
                .toList();
    }

    private static String cacheKey(ActivityMapRow r) {
        return r.getId() + ":" + r.getTotalDistance();
    }

    private static String simplify(String polyline) {
        if (polyline == null || polyline.isEmpty()) return "";
        return PolylineEncoder.encodeLatLng(PolylineEncoder.simplify(PolylineEncoder.decode(polyline), SIMPLIFY_TOLERANCE_DEG));
    }
}

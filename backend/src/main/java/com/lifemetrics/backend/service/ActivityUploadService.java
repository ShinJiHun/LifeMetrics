package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.UploadResultDto;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.ActivityPoint;
import com.lifemetrics.backend.entity.GearUsage;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.ActivityPointRepository;
import com.lifemetrics.backend.repository.GearUsageRepository;
import com.lifemetrics.backend.util.PolylineEncoder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActivityUploadService {

    @Value("${parser.api.url}")
    private String parserApiUrl;

    private final RestTemplate restTemplate;
    private final ActivityCoreRepository activityCoreRepository;
    private final ActivityPointRepository pointRepository;
    private final GearUsageRepository gearUsageRepository;

    public UploadResultDto.FileResult processFile(MultipartFile file, String rideType, String permanentNo, String permanentGpxFile) {
        String filename = file.getOriginalFilename();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            // ★ 라이딩 타입(퍼머넌트/브레베/...) 선택값을 파서 마이크로서비스로 함께 전달
            // (parser 쪽에서 activity_core.ride_type / permanent_no / permanent_gpx_file 로 저장 —
            //  parser 쪽도 permanent_gpx_file 컬럼을 받아 저장하도록 맞춰야 함)
            if (rideType != null && !rideType.isBlank()) {
                body.add("rideType", rideType);
            }
            if (permanentNo != null && !permanentNo.isBlank()) {
                body.add("permanentNo", permanentNo);
            }
            if (permanentGpxFile != null && !permanentGpxFile.isBlank()) {
                body.add("permanentGpxFile", permanentGpxFile);
            }

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    parserApiUrl + "/parse", request, Map.class);

            String status = (String) response.getBody().get("status");
            return new UploadResultDto.FileResult(filename, status, null);

        } catch (Exception e) {
            return new UploadResultDto.FileResult(filename, "fail", e.getMessage());
        }
    }

    @Transactional
    public void mergeActivities(Long parentId, Long targetId) {
        if (parentId.equals(targetId)) {
            throw new IllegalArgumentException("parent와 target이 동일한 활동입니다: " + parentId);
        }

        // 1. 엔티티 조회
        ActivityCore parent = activityCoreRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent activity not found: " + parentId));
        ActivityCore target = activityCoreRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target activity not found: " + targetId));

        // 2. 포인트 seq 재조정 및 이동
        // ActivityPoint는 (activityCoreId, seq) 복합 PK라서 엔티티를 로드해 필드를 바꾸면
        // Hibernate가 "identifier was altered" 예외를 던진다. 벌크 UPDATE로 한 번에 옮긴다.
        Integer maxSeqObj = pointRepository.findMaxSeqByActivityCoreId(parentId);
        int parentMaxSeq = (maxSeqObj != null) ? maxSeqObj : -1;
        pointRepository.reassignToParent(targetId, parentId, parentMaxSeq + 1);

        // 3. GearUsage 이동 (중복 방지 및 합산 로직)
        List<GearUsage> targetGears = gearUsageRepository.findByActivityCoreId(targetId);
        for (GearUsage tg : targetGears) {
            GearUsage existing = gearUsageRepository.findByActivityCoreIdAndFrontGearAndRearGearAndTerrain(
                    parentId, tg.getFrontGear(), tg.getRearGear(), tg.getTerrain()
            );

            if (existing != null) {
                existing.setDurationSec(safeAdd(existing.getDurationSec(), tg.getDurationSec()));
                existing.setDistance(safeAdd(existing.getDistance(), tg.getDistance()));
                gearUsageRepository.delete(tg);
            } else {
                tg.setActivityCoreId(parentId);
            }
        }

        // 4. 통계치 합산 및 업데이트
        // parent에 자전거 매칭이 안 돼 있으면(bike_id null) 병합 결과 전체가
        // 어느 자전거의 누적 거리에도 안 잡히던 문제가 있었다 → target 것을 이어받는다.
        if (parent.getBikeId() == null) {
            parent.setBikeId(target.getBikeId());
        }

        parent.setEndTime(target.getEndTime());
        parent.setTotalDistance(safeAdd(parent.getTotalDistance(), target.getTotalDistance()));
        parent.setMovingTime(safeAdd(parent.getMovingTime(), target.getMovingTime()));
        parent.setElapsedTime(safeAdd(parent.getElapsedTime(), target.getElapsedTime()));
        parent.setTotalAscent(safeAdd(parent.getTotalAscent(), target.getTotalAscent()));
        parent.setTotalDescent(safeAdd(parent.getTotalDescent(), target.getTotalDescent()));
        parent.setCalories(safeAdd(parent.getCalories(), target.getCalories()));

        parent.setMaxSpeed(Math.max(safeVal(parent.getMaxSpeed()), safeVal(target.getMaxSpeed())));
        parent.setMaxPower(Math.max(safeVal(parent.getMaxPower()), safeVal(target.getMaxPower())));

        parent.setEndLat(target.getEndLat());
        parent.setEndLon(target.getEndLon());

        // 5. 지도 표시용 polyline을 합쳐진 포인트 전체로 재인코딩
        // (그대로 두면 parent 원본 경로만 남아 지도에 target 구간이 안 그려진다)
        List<ActivityPoint> mergedPoints = pointRepository.findByActivityCoreIdOrderBySeqAsc(parentId);
        parent.setPolyline(PolylineEncoder.encode(mergedPoints));

        // 6. 서브 활동 삭제 (Cascade 설정에 따라 포인트도 함께 정리됨)
        activityCoreRepository.delete(target);

    }

    /** polyline이 실제 포인트와 어긋난 활동(예: 이 수정 이전에 병합된 것)을 바로잡는 복구용 메서드. */
    @Transactional
    public void recomputePolyline(Long activityId) {
        ActivityCore activity = activityCoreRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
        List<ActivityPoint> points = pointRepository.findByActivityCoreIdOrderBySeqAsc(activityId);
        activity.setPolyline(PolylineEncoder.encode(points));
    }

    /** bike_id가 잘못 잡혀 있거나(예: 병합 전 이 수정 이전 케이스) 비어 있는 활동을 바로잡는 복구용 메서드. */
    @Transactional
    public void fixBikeId(Long activityId, Long bikeId) {
        ActivityCore activity = activityCoreRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
        activity.setBikeId(bikeId);
    }

    // Null-safe 덧셈 및 값 추출 헬퍼 메서드들
    private double safeAdd(Double d1, Double d2) {
        return (d1 != null ? d1 : 0.0) + (d2 != null ? d2 : 0.0);
    }

    private int safeAdd(Integer i1, Integer i2) {
        return (i1 != null ? i1 : 0) + (i2 != null ? i2 : 0);
    }

    private double safeVal(Double d) {
        return d != null ? d : 0.0;
    }
}
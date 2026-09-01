package com.lifemetrics.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.Bike;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.BikeRepository;
import com.lifemetrics.backend.repository.BikeTotals;
import com.lifemetrics.backend.repository.GearUsageRepository;
import com.lifemetrics.backend.repository.GearUsageSummary;
import com.lifemetrics.backend.service.BikeSpecExtractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bikes")
@RequiredArgsConstructor
public class BikeController {

    private final BikeSpecExtractService extractService;
    private final BikeRepository bikeRepository;
    private final ActivityCoreRepository activityCoreRepository;
    private final GearUsageRepository gearUsageRepository;

    /**
     * 가동중 + 종료 전부 반환한다. 화면에서 is_retired 로 두 그룹으로 나눈다.
     *
     * 누적거리/누적시간은 bike 테이블 컬럼을 믿지 않고 activity_core 에서 집계해 덮어쓴다.
     * (컬럼을 갱신하는 코드가 없어 값이 실제와 어긋나 있다.)
     * 시간은 이동시간(moving_time)과 총시간(elapsed_time)을 따로 내려준다.
     * readOnly = true 라서 아래 setter 는 DB 로 flush 되지 않는다.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Bike> list() {
        Map<Long, BikeTotals> totals = activityCoreRepository.sumTotalsByBike().stream()
                .collect(Collectors.toMap(BikeTotals::getBikeId, t -> t));

        List<Bike> bikes = bikeRepository.findAllByOrderByIsRetiredAscIsDefaultDescIdDesc();
        for (Bike bike : bikes) {
            BikeTotals t = totals.get(bike.getId());
            bike.setTotalTime(t == null ? 0 : t.getTotalMovingTime().intValue());
            bike.setTotalElapsedTime(t == null ? 0 : t.getTotalElapsedTime().intValue());
            bike.setTotalDistance(t == null ? 0.0 : t.getTotalDistance());
        }
        return bikes;
    }

    /** 자전거 한 대의 상세 정보. 누적거리/시간은 list()와 동일하게 activity_core 집계로 덮어쓴다. */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Bike> get(@PathVariable Long id) {
        return bikeRepository.findById(id)
                .map(bike -> {
                    BikeTotals t = activityCoreRepository.sumTotalsByBike().stream()
                            .filter(x -> x.getBikeId().equals(id))
                            .findFirst().orElse(null);
                    bike.setTotalTime(t == null ? 0 : t.getTotalMovingTime().intValue());
                    bike.setTotalElapsedTime(t == null ? 0 : t.getTotalElapsedTime().intValue());
                    bike.setTotalDistance(t == null ? 0.0 : t.getTotalDistance());
                    return ResponseEntity.ok(bike);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 자전거 한 대가 사용된 모든 활동의 기어 사용량을 (앞기어, 뒷기어, 지형)별로 합산해 반환한다.
     * front_gear/rear_gear는 FIT에서 관측된 실제 체인링/코그 톱니수(T)라 인덱스가 아니다 —
     * 프론트에서 앞기어 값 중 큰 쪽을 아우터, 작은 쪽을 이너로 나눠 쓰면 된다.
     */
    @GetMapping("/{id}/gear-usage")
    @Transactional(readOnly = true)
    public List<GearUsageSummary> gearUsage(@PathVariable Long id) {
        return gearUsageRepository.summarizeByBikeId(id);
    }

    // 모델명 검색 → 사양 prefill JSON (저장 안 함)
    @PostMapping("/search-spec")
    public ResponseEntity<JsonNode> searchSpec(@RequestBody Map<String, String> req) {
        return ResponseEntity.ok(extractService.searchSpec(req.get("model")));
    }

    // 스펙 이미지 업로드 → 사양 prefill JSON (저장 안 함)
    @PostMapping(value = "/extract-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JsonNode> extractImage(@RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(extractService.extractFromImage(image));
    }

    // 폼에서 검수/수정한 최종 자전거 저장
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Bike> save(@RequestBody Bike bike) {
        if (bike.getName() == null || bike.getName().isBlank())
            bike.setName(bike.getModel() != null ? bike.getModel() : bike.getBrand());
        return ResponseEntity.ok(bikeRepository.save(bike));
    }
}
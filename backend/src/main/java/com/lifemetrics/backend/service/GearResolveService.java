package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.GearContext;
import com.lifemetrics.backend.entity.Bike;
import com.lifemetrics.backend.entity.DeviceComponent;
import com.lifemetrics.backend.entity.DeviceInfo;
import com.lifemetrics.backend.repository.BikeRepository;
import com.lifemetrics.backend.repository.DeviceComponentRepository;
import com.lifemetrics.backend.repository.DeviceInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GearResolveService {

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceComponentRepository deviceComponentRepository;
    private final BikeRepository bikeRepository;

    /**
     * 활동에 매칭된 자전거(activity_core.bike_id) 기준으로 기어 정보를 만든다.
     * bike_id 가 없는 과거 활동은 기존 방식(활동 시각에 활성이던 device_info)으로 폴백한다.
     */
    @Transactional(readOnly = true)
    public GearContext resolve(Long userId, LocalDateTime activityStart, Long bikeId) {
        GearContext base = resolve(userId, activityStart);

        if (bikeId == null) return base;

        String label = bikeRepository.findById(bikeId)
                .map(this::bikeLabel)
                .orElse(null);
        if (label == null) return base;

        // 컴포넌트(체인링/카세트/타이어)는 device_info 쪽에만 있으므로 그대로 두고
        // 자전거 식별 정보만 bike 테이블 값으로 덮어쓴다.
        if (base == null) {
            return new GearContext(bikeId, label, null, null, null, Map.of());
        }
        return new GearContext(bikeId, label, base.chainring(), base.cassette(), base.tire(), base.etc());
    }

    private String bikeLabel(Bike bike) {
        if (bike.getName() != null && !bike.getName().isBlank()) {
            return bike.getName();
        }
        String brand = bike.getBrand() != null ? bike.getBrand() : "";
        String model = bike.getModel() != null ? bike.getModel() : "";
        String joined = (brand + " " + model).trim();
        return joined.isEmpty() ? null : joined;
    }

    @Transactional(readOnly = true)
    public GearContext resolve(Long userId, LocalDateTime activityStart) {
        if (userId == null || activityStart == null) return null;

        DeviceInfo bike = deviceInfoRepository.findActiveBikesAt(userId, activityStart)
                .stream()
                .findFirst()
                .orElse(null);
        if (bike == null) return null;

        List<DeviceComponent> components =
                deviceComponentRepository.findEffectiveAt(bike.getId(), activityStart.toLocalDate());

        String chainring = null;
        String cassette = null;
        String tire = null;
        Map<String, String> etc = new LinkedHashMap<>();

        for (DeviceComponent c : components) {
            String type = c.getComponentType();
            String spec = c.getSpec();
            if (type == null) continue;
            switch (type) {
                case "CHAINRING" -> chainring = spec;
                case "CASSETTE"  -> cassette  = spec;
                case "TIRE"      -> tire      = spec;
                default          -> etc.put(type, spec);
            }
        }

        return new GearContext(bike.getId(), bikeLabel(bike), chainring, cassette, tire, etc);
    }

    private String bikeLabel(DeviceInfo bike) {
        if (bike.getUserLabel() != null && !bike.getUserLabel().isBlank()) {
            return bike.getUserLabel();
        }
        String mfr = bike.getManufacturer() != null ? bike.getManufacturer() : "";
        String model = bike.getModel() != null ? bike.getModel() : "";
        String joined = (mfr + " " + model).trim();
        return joined.isEmpty() ? null : joined;
    }
}
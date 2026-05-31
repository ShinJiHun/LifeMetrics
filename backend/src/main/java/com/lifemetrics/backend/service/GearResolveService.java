package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.GearContext;
import com.lifemetrics.backend.entity.DeviceComponent;
import com.lifemetrics.backend.entity.DeviceInfo;
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
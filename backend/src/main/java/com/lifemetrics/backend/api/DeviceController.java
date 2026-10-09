package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.DeviceDto;
import com.lifemetrics.backend.entity.DeviceInfo;
import com.lifemetrics.backend.repository.DeviceInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 센서·기기 관리. device_info 에 같이 들어 있는 BIKE 행은 자전거 메뉴에서 관리하므로 제외한다.
 * 쓰기(POST/PUT)는 AdminWriteFilter 가 관리자만 허용한다. 삭제 대신 사용 중지(isActive=false)로 남긴다.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private static final Set<String> DEVICE_TYPES =
            Set.of("HEAD_UNIT", "SPEED_SENSOR", "CADENCE_SENSOR", "HEART_RATE", "POWER_METER");

    private final DeviceInfoRepository deviceRepository;

    @GetMapping
    public List<DeviceDto> list(@RequestParam(defaultValue = "1") Long userId) {
        return deviceRepository.findAll().stream()
                .filter(d -> userId.equals(d.getOwnerUserId()) && DEVICE_TYPES.contains(d.getDeviceType()))
                .sorted(Comparator.comparing((DeviceInfo d) -> !Boolean.TRUE.equals(d.getIsActive()))
                        .thenComparing(DeviceInfo::getDeviceType)
                        .thenComparing(DeviceInfo::getId))
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public ResponseEntity<DeviceDto> create(@RequestParam(defaultValue = "1") Long userId,
                                            @RequestBody DeviceDto request) {
        if (!DEVICE_TYPES.contains(request.getDeviceType())) return ResponseEntity.badRequest().build();
        DeviceInfo device = new DeviceInfo();
        device.setOwnerUserId(userId);
        device.setCreatedAt(LocalDateTime.now());
        apply(device, request);
        return ResponseEntity.ok(toDto(deviceRepository.save(device)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceDto> update(@PathVariable Long id, @RequestBody DeviceDto request) {
        if (!DEVICE_TYPES.contains(request.getDeviceType())) return ResponseEntity.badRequest().build();
        return deviceRepository.findById(id)
                .filter(d -> DEVICE_TYPES.contains(d.getDeviceType()))
                .map(device -> {
                    apply(device, request);
                    return ResponseEntity.ok(toDto(deviceRepository.save(device)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void apply(DeviceInfo device, DeviceDto request) {
        device.setDeviceType(request.getDeviceType());
        device.setManufacturer(request.getManufacturer());
        device.setModel(request.getModel());
        device.setUserLabel(request.getUserLabel());
        device.setSerialNumber(request.getSerialNumber());
        device.setFirmwareVersion(request.getFirmwareVersion());
        device.setIsActive(request.getIsActive() == null || request.getIsActive());
        device.setFirstSeenAt(atStartOfDay(request.getStartDate()));
        device.setLastSeenAt(atStartOfDay(request.getEndDate()));
        device.setUpdatedAt(LocalDateTime.now());
    }

    private DeviceDto toDto(DeviceInfo d) {
        DeviceDto dto = new DeviceDto();
        dto.setId(d.getId());
        dto.setDeviceType(d.getDeviceType());
        dto.setManufacturer(d.getManufacturer());
        dto.setModel(d.getModel());
        dto.setUserLabel(d.getUserLabel());
        dto.setSerialNumber(d.getSerialNumber());
        dto.setFirmwareVersion(d.getFirmwareVersion());
        dto.setIsActive(d.getIsActive());
        dto.setStartDate(d.getFirstSeenAt() != null ? d.getFirstSeenAt().toLocalDate() : null);
        dto.setEndDate(d.getLastSeenAt() != null ? d.getLastSeenAt().toLocalDate() : null);
        return dto;
    }

    private static LocalDateTime atStartOfDay(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }
}

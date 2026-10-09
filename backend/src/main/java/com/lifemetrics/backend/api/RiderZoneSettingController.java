package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.RiderZoneSettingDto;
import com.lifemetrics.backend.service.RiderZoneSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rider/zone-setting")
@RequiredArgsConstructor
public class RiderZoneSettingController {

    private final RiderZoneSettingService settingService;

    @GetMapping
    public RiderZoneSettingDto get(@RequestParam(defaultValue = "1") Long userId) {
        return settingService.getEffective(userId);
    }

    // Body: { "ftp": 230, "maxHr": 185 }  (null 이면 추정값으로 되돌림)
    @PutMapping
    public RiderZoneSettingDto save(@RequestParam(defaultValue = "1") Long userId,
                                    @RequestBody RiderZoneSettingDto request) {
        return settingService.save(userId, request.getFtp(), request.getMaxHr());
    }
}

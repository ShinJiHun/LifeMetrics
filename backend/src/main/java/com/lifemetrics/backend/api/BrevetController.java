package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.BrevetDto;
import com.lifemetrics.backend.service.BrevetScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/brevet")
@RequiredArgsConstructor
public class BrevetController {

    private final BrevetScheduleService brevetScheduleService;

    /**
     * 2026 브레베 일정 전체
     * GET /api/brevet/schedule
     */
    @GetMapping("/schedule")
    public List<BrevetDto> getSchedule() throws IOException {
        return brevetScheduleService.fetchSchedule();
    }

    /**
     * 특정 거리만 필터
     * GET /api/brevet/schedule?distance=200
     */
    @GetMapping("/schedule/filter")
    public List<BrevetDto> getByDistance(
            @RequestParam(required = false) String distance,
            @RequestParam(required = false) String region
    ) throws IOException {
        return brevetScheduleService.fetchSchedule().stream()
                .filter(b -> distance == null || b.getDistance().equals(distance))
                .filter(b -> region == null || b.getRegion().contains(region))
                .toList();
    }
}

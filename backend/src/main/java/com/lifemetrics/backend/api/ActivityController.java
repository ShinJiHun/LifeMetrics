package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.ActivityDetailDto;
import com.lifemetrics.backend.dto.ActivitySegmentDto;
import com.lifemetrics.backend.dto.ActivitySummaryDto;
import com.lifemetrics.backend.dto.MonthlyStatsDto;
import com.lifemetrics.backend.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

// ActivityController.java
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    // 라이딩 목록
    // ActivityController.java - getList 메서드 수정
    @GetMapping("/list")
    public List<ActivitySummaryDto> getList(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return activityService.getActivityList(userId, startDate, endDate, page, size);
    }

    // 라이딩 상세
    @GetMapping("/{id}")
    public ActivityDetailDto getDetail(@PathVariable Long id) {
        return activityService.getActivityDetail(id);
    }

    // 월간 통계
    @GetMapping("/stats/monthly")
    public MonthlyStatsDto getMonthlyStats(
            @RequestParam Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        return activityService.getMonthlyStats(userId, year, month);
    }


    @GetMapping("/{id}/segments")
    public List<ActivitySegmentDto> getSegments(@PathVariable Long id) {
        return activityService.getActivitySegments(id);
    }

    // 포인트 없는 경량 상세 조회
    @GetMapping("/{id}/summary")
    public ActivitySummaryDto getSummary(@PathVariable Long id) {
        return activityService.getActivitySummary(id);
    }

}

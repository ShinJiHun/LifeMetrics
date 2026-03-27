package com.lifemetrics.backend.api;

import com.lifemetrics.backend.entity.SegmentEffort;
import com.lifemetrics.backend.repository.SegmentEffortRepository;
import com.lifemetrics.backend.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/segments")
@RequiredArgsConstructor
public class SegmentController {

    private final SegmentEffortRepository segmentEffortRepository;
    private final SegmentRepository segmentRepository;

    @GetMapping("/{segmentId}/efforts")
    public ResponseEntity<?> getEfforts(
            @PathVariable Long segmentId,
            @RequestParam(required = false) Long activityId
    ) {
        if (activityId != null) {
            return segmentEffortRepository
                    .findByActivityCoreIdAndSegmentId(activityId, segmentId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.ok(
                segmentEffortRepository.findBySegmentIdOrderByElapsedTimeSecAsc(segmentId)
        );
    }
}

package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.BodyRecordResponse;
import com.lifemetrics.backend.service.BodyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/body")
@RequiredArgsConstructor
public class BodyController {

    private final BodyService bodyService;

    @GetMapping("/records")
    public BodyRecordResponse getBodyRecords(
            @RequestParam(defaultValue = "1") Long userId
    ) {
        return bodyService.getBodyRecords(userId);
    }
}

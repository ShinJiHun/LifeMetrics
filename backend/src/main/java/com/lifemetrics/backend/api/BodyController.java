package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.BodyRecordsResponse;
import com.lifemetrics.backend.service.BodyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/body")
@RequiredArgsConstructor
public class BodyController {

    private final BodyService bodyService;

    @GetMapping("/records")
    public BodyRecordsResponse getBodyRecords(@RequestParam(defaultValue = "1") Long userId) {
        return bodyService.getBodyRecords(userId);
    }

    @PostMapping("/inbody/upload")
    public ResponseEntity<Map<String, Object>> uploadInbodyImage(@RequestParam("file") MultipartFile file) {
        return bodyService.uploadInbodyImage(file);
    }
}
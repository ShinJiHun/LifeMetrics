package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.UploadResultDto;
import com.lifemetrics.backend.service.ActivityUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityUploadController {

    private final ActivityUploadService uploadService;

    @PostMapping("/upload")
    public ResponseEntity<UploadResultDto> upload(
            @RequestParam("files") List<MultipartFile> files) {

        List<UploadResultDto.FileResult> results = files.stream()
                .map(uploadService::processFile)
                .toList();

        return ResponseEntity.ok(new UploadResultDto(results));
    }
}

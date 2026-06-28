package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.UploadResultDto;
import com.lifemetrics.backend.notify.SlackNotifier;
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
    private final SlackNotifier slackNotifier;

    @PostMapping("/upload")
    public ResponseEntity<UploadResultDto> upload(
            @RequestParam("files") List<MultipartFile> files) {

        List<UploadResultDto.FileResult> results = files.stream()
                .map(uploadService::processFile)
                .toList();

        slackNotifier.send(buildUploadSummary(results));

        return ResponseEntity.ok(new UploadResultDto(results));
    }

    /** 업로드 결과를 Slack 알림용 요약 메시지로 변환한다. */
    private String buildUploadSummary(List<UploadResultDto.FileResult> results) {
        long success = results.stream().filter(r -> "success".equals(r.getStatus())).count();
        long fail = results.size() - success;

        StringBuilder sb = new StringBuilder();
        sb.append("📥 *활동 업로드 완료* — 총 ").append(results.size())
                .append("개 (성공 ").append(success).append(", 실패 ").append(fail).append(")\n");

        results.forEach(r -> {
            String icon = "success".equals(r.getStatus()) ? "✅" : "❌";
            sb.append(icon).append(" ").append(r.getFilename());
            if (r.getError() != null && !r.getError().isBlank()) {
                sb.append(" — ").append(r.getError());
            }
            sb.append("\n");
        });

        return sb.toString().trim();
    }
}
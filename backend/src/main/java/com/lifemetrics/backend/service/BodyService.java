package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.BodyRecordResponse;
import com.lifemetrics.backend.dto.BodyRecordsResponse;
import com.lifemetrics.backend.entity.UserInbodyRecord;
import com.lifemetrics.backend.repository.UserInbodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BodyService {

    private final UserInbodyRecordRepository inbodyRepo;

    @Value("${inbody.upload.path:/mnt/200gb/NAS/inbody/}")
    private String uploadPath;

    public BodyRecordsResponse getBodyRecords(Long userId) {
        List<UserInbodyRecord> records = inbodyRepo.findByUserIdOrderByRecordDate(userId);

        if (records.isEmpty()) {
            return new BodyRecordsResponse(List.of(), null);
        }

        List<BodyRecordResponse> responses = new java.util.ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            UserInbodyRecord curr = records.get(i);
            UserInbodyRecord prev = (i > 0) ? records.get(i - 1) : null;

            responses.add(toResponse(curr, prev));
        }

        BodyRecordResponse latest = responses.get(responses.size() - 1);
        return new BodyRecordsResponse(responses, latest);
    }

    public ResponseEntity<Map<String, Object>> uploadInbodyImage(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            Path filePath = Paths.get(uploadPath, fileName);

            Files.createDirectories(filePath.getParent());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "업로드 완료. 처리 중입니다.",
                    "fileName", fileName
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "업로드 실패: " + e.getMessage()
            ));
        }
    }

    private BodyRecordResponse toResponse(
            UserInbodyRecord curr,
            UserInbodyRecord prev
    ) {
        BodyRecordResponse dto = new BodyRecordResponse();

        dto.setRecordDate(curr.getRecordDate());
        dto.setWeight(curr.getWeight());
        dto.setSkeletalMuscleMass(curr.getSkeletalMuscleMass());
        dto.setBodyFatMass(curr.getBodyFatMass());
        dto.setBodyFatPercentage(curr.getBodyFatPercentage());
        dto.setBmi(curr.getBmi());
        dto.setVisceralFatLevel(curr.getVisceralFatLevel());
        dto.setIsMeasured(curr.getIsMeasured());

        dto.setWeightDelta(calc(curr.getWeight(),
                prev != null ? prev.getWeight() : null));

        dto.setSkeletalMuscleMassDelta(calc(
                curr.getSkeletalMuscleMass(),
                prev != null ? prev.getSkeletalMuscleMass() : null
        ));

        dto.setBodyFatPercentageDelta(calc(
                curr.getBodyFatPercentage(),
                prev != null ? prev.getBodyFatPercentage() : null
        ));

        return dto;
    }

    private Double calc(Double curr, Double prev) {
        return (curr != null && prev != null) ? curr - prev : null;
    }
}
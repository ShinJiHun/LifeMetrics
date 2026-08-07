package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.BodyRecordResponse;
import com.lifemetrics.backend.dto.BodyRecordsResponse;
import com.lifemetrics.backend.dto.WeightRequest;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BodyService {

    private final UserBodyRecordRepository bodyRecordRepo;

    // BodyService.java - uploadInbodyImage 수정
    @Value("${inbody.upload.path:/mnt/200gb/NAS/inbody/}")
    private String inbodyUploadPath;

    @Value("${fitdays.upload.path:/mnt/200gb/NAS/fitdays/}")
    private String fitdaysUploadPath;

    public BodyRecordsResponse getBodyRecords(Long userId) {
        List<UserBodyRecord> records = bodyRecordRepo.findByUserIdOrderByRecordDate(userId);

        if (records.isEmpty()) {
            return new BodyRecordsResponse(List.of(), null);
        }

        List<BodyRecordResponse> responses = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            UserBodyRecord curr = records.get(i);
            UserBodyRecord prev = (i > 0) ? records.get(i - 1) : null;
            responses.add(toResponse(curr, prev));
        }

        BodyRecordResponse latest = responses.get(responses.size() - 1);
        return new BodyRecordsResponse(responses, latest);
    }

    // 타입별 조회
    public BodyRecordsResponse getBodyRecordsByType(Long userId, MeasurementType type) {
        List<UserBodyRecord> records = bodyRecordRepo
                .findByUserIdAndMeasurementTypeOrderByRecordDate(userId, type);

        if (records.isEmpty()) {
            return new BodyRecordsResponse(List.of(), null);
        }

        List<BodyRecordResponse> responses = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            UserBodyRecord curr = records.get(i);
            UserBodyRecord prev = (i > 0) ? records.get(i - 1) : null;
            responses.add(toResponse(curr, prev));
        }

        BodyRecordResponse latest = responses.get(responses.size() - 1);
        return new BodyRecordsResponse(responses, latest);
    }

    public ResponseEntity<Map<String, Object>> uploadImage(MultipartFile file, String type) {
        try {
            String uploadPath = "FITDAYS".equals(type) ? fitdaysUploadPath : inbodyUploadPath;
            String fileName = file.getOriginalFilename();
            Path filePath = Paths.get(uploadPath, fileName);

            Files.createDirectories(filePath.getParent());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "업로드 완료. 처리 중입니다.",
                    "fileName", fileName,
                    "type", type
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "업로드 실패: " + e.getMessage()
            ));
        }
    }

    private BodyRecordResponse toResponse(UserBodyRecord curr, UserBodyRecord prev) {
        BodyRecordResponse dto = new BodyRecordResponse();

        dto.setRecordDate(curr.getRecordDate());
        dto.setMeasurementType(curr.getMeasurementType());
        dto.setWeight(curr.getWeight());
        dto.setSkeletalMuscleMass(curr.getSkeletalMuscleMass());
        dto.setBodyFatMass(curr.getBodyFatMass());
        dto.setFatFreeMass(fatFreeMass(curr));
        dto.setBodyFatPercentage(curr.getBodyFatPercentage());
        dto.setBmi(curr.getBmi());
        dto.setVisceralFatLevel(curr.getVisceralFatLevel());
        dto.setBodyWater(curr.getBodyWater());
        dto.setBoneMass(curr.getBoneMass());
        dto.setBasalMetabolicRate(curr.getBasalMetabolicRate());
        dto.setRawLlmJson(curr.getRawLlmJson());

        dto.setWeightDelta(calc(curr.getWeight(),
                prev != null ? prev.getWeight() : null));
        dto.setSkeletalMuscleMassDelta(calc(curr.getSkeletalMuscleMass(),
                prev != null ? prev.getSkeletalMuscleMass() : null));
        dto.setBodyFatPercentageDelta(calc(curr.getBodyFatPercentage(),
                prev != null ? prev.getBodyFatPercentage() : null));
        dto.setFatFreeMassDelta(calc(fatFreeMass(curr),
                prev != null ? fatFreeMass(prev) : null));

        return dto;
    }

    /**
     * 제지방량. 저장된 값을 우선하고, 없으면 체중 - 체지방량으로 채운다.
     * 인바디 기록지의 제지방량과 일치하는 항등식이다.
     */
    private Double fatFreeMass(UserBodyRecord r) {
        if (r.getFatFreeMass() != null) return r.getFatFreeMass();
        if (r.getWeight() == null || r.getBodyFatMass() == null) return null;
        return Math.round((r.getWeight() - r.getBodyFatMass()) * 10) / 10.0;
    }

    private Double calc(Double curr, Double prev) {
        return (curr != null && prev != null) ? curr - prev : null;
    }

    // BodyService에 추가
    public void saveWeight(WeightRequest req) {
        UserBodyRecord record = new UserBodyRecord();
        record.setUserId(req.getUserId());
        record.setRecordDate(LocalDate.parse(req.getRecordDate()));
        record.setMeasurementType(MeasurementType.FITDAYS);
        record.setWeight(req.getWeight());
        record.setBodyFatPercentage(req.getBodyFatPercentage());
        bodyRecordRepo.save(record);
    }
}

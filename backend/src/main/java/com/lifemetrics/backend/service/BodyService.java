package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.BodyRecordResponse;
import com.lifemetrics.backend.dto.BodyRecordsResponse;
import com.lifemetrics.backend.entity.UserInbodyRecord;
import com.lifemetrics.backend.repository.UserInbodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BodyService {

    private final UserInbodyRecordRepository inbodyRepo;

    public BodyRecordsResponse getBodyRecords(Long userId) {

        List<UserInbodyRecord> records =
                inbodyRepo.findByUserIdOrderByRecordDate(userId);

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

        // ✅ 반드시 추가
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

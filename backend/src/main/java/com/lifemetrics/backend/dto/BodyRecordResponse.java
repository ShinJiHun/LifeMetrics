package com.lifemetrics.backend.dto;

import com.lifemetrics.backend.entity.UserInbodyRecord;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BodyRecordResponse {

    private LocalDate recordDate;

    private Double weight;
    private Double skeletalMuscleMass;
    private Double bodyFatMass;
    private Double bodyFatPercentage;

    private Double bmi;
    private Integer visceralFatLevel;

    private Boolean isMeasured;
    private Double weightDelta;
    private Double skeletalMuscleMassDelta;
    private Double bodyFatPercentageDelta;

    public BodyRecordResponse() {
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

        // ✅ 추가해야 하는 핵심 필드들
        dto.setBmi(curr.getBmi());
        dto.setVisceralFatLevel(curr.getVisceralFatLevel());
        dto.setIsMeasured(curr.getIsMeasured());

        // delta 계산
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


    private Double calc(Double a, Double b) {
        return (a != null && b != null) ? a - b : null;
    }
}

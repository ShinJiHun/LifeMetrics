package com.lifemetrics.backend.service;

import com.lifemetrics.backend.domain.BodyType;
import com.lifemetrics.backend.dto.HumanModelResponse;
import com.lifemetrics.backend.entity.UserInbodyRecord;
import com.lifemetrics.backend.repository.UserInbodyRecordRepository;
import com.lifemetrics.backend.repository.resolver.BodyTypeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HumanModelService {

    private final UserInbodyRecordRepository inbodyRepo;
    private final BodyTypeResolver bodyTypeResolver;

    public HumanModelResponse getHumanModel(Long userId) {

        UserInbodyRecord latest =
                inbodyRepo.findTopByUserIdOrderByRecordDateDesc(userId)
                        .orElseThrow(() -> new IllegalStateException("No body record"));

        BodyType bodyType = bodyTypeResolver.resolve(
                latest.getBodyFatPercentage(),
                latest.getSkeletalMuscleMass(),
                latest.getBmi(),
                "M" // TODO: UserProfile에서 가져오도록 개선
        );

        HumanModelResponse res = new HumanModelResponse();
        res.setBodyType(bodyType.name().toLowerCase());

        // 기본 정보
        res.setWeight(latest.getWeight());
        res.setBodyFatPercentage(latest.getBodyFatPercentage());
        res.setSkeletalMuscleMass(latest.getSkeletalMuscleMass());

        // ⛏️ 다음 단계에서 쓸 scale 값 (지금은 기본값)
        res.setMuscleScale(1.0);
        res.setFatScale(1.0);
        res.setWaistScale(1.0);

        res.setRecordDate(latest.getRecordDate().toString());

        return res;
    }
}

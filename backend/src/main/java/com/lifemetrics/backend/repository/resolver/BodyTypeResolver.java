package com.lifemetrics.backend.repository.resolver;

import com.lifemetrics.backend.domain.BodyType;
import org.springframework.stereotype.Component;
@Component
public class BodyTypeResolver {

    public BodyType resolve(
            Double bodyFatPercentage,
            Double skeletalMuscleMass,
            Double bmi,
            String gender
    ) {
        // 방어
        if (bodyFatPercentage == null || skeletalMuscleMass == null) {
            return BodyType.NORMAL;
        }

        // 💪 ATHLETE
        if (bodyFatPercentage < 12 && skeletalMuscleMass > 35) {
            return BodyType.ATHLETE;
        }

        // 🏋️ FIT
        if (bodyFatPercentage < 18 && skeletalMuscleMass > 30) {
            return BodyType.FIT;
        }

        // 🙂 NORMAL
        if (bodyFatPercentage < 25) {
            return BodyType.NORMAL;
        }

        // ⚠️ OVERWEIGHT
        if (bodyFatPercentage < 30) {
            return BodyType.OVERWEIGHT;
        }

        // ❗ OBESE
        return BodyType.OBESE;
    }
}

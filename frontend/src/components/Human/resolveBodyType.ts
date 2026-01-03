// src/components/Human/resolveBodyType.ts
import type { BodySummaryRecord } from "@/types/BodySummaryRecord";
import type { BodyType } from "./human.types";

export function resolveBodyType(
    record: BodySummaryRecord
): BodyType {
    const {
        bmi,
        body_fat_percentage: pbf,
        skeletal_muscle_mass: smm,
    } = record;

    // 1️⃣ 비만
    if (bmi >= 30 || pbf >= 30) {
        return "obese";
    }

    // 2️⃣ 과체중
    if (bmi >= 25 || pbf >= 25) {
        return "overweight";
    }

    // 3️⃣ 마른 체형
    if (bmi < 18.5 && pbf < 15) {
        return "lean";
    }

    // 4️⃣ 근육 최상 (운동 고인물 / 레퍼런스)
    if (pbf <= 15 && smm >= 36) {
        return "athlete";
    }

    // 5️⃣ 운동형
    if (pbf <= 22 && smm >= 33) {
        return "fit";
    }

    // 6️⃣ 나머지
    return "normal";
}

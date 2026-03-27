// components/Human/humanScale.ts
import type { BodySummaryRecord } from "@/types/BodySummaryRecord";

export function calcHumanScale(summary: BodySummaryRecord) {
    const weight = summary.weight ?? 80;
    const fat = summary.bodyFatPercentage ?? 25;        // ✅ 수정
    const muscle = summary.skeletalMuscleMass ?? 30;   // ✅ 수정

    // 몸통 크기 (체중 + 근육)
    const body =
        0.9 +
        weight / 120 +
        muscle / 80;

    // 배 크기 (체지방률 중심)
    const belly =
        0.75 +
        fat / 100;

    return {
        body,
        belly,
    };
}

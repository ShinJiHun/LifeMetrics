// components/Human/humanScale.ts
import type { BodySummaryRecord } from "@/types/BodySummaryRecord";

export function calcHumanScale(summary: BodySummaryRecord) {
    const weight = summary.weight ?? 80;
    const fat = summary.body_fat_percentage ?? 25;
    const muscle = summary.skeletal_muscle_mass ?? 30;

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

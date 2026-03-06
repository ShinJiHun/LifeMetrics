import type { BodySummaryRecord } from "@/types/BodySummaryRecord";
import type { BodyType } from "./human.types";

export function resolveBodyType(record: BodySummaryRecord): BodyType {
    const bmi = record.bmi;
    const pbf = record.bodyFatPercentage;
    const smm = record.skeletalMuscleMass;

    if (bmi != null && (bmi >= 30 || pbf >= 30)) return "obese";
    if (bmi != null && (bmi >= 25 || pbf >= 25)) return "overweight";
    if (bmi != null && bmi < 18.5 && pbf < 15) return "lean";
    if (pbf <= 15 && smm >= 36) return "athlete";
    if (pbf <= 22 && smm >= 33) return "fit";
    return "normal";
}

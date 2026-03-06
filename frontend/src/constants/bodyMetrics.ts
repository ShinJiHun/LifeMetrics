import type { MetricKey } from "@/types/MetricKey";

export const BODY_METRICS: { key: MetricKey; label: string; unit: string }[] = [
    { key: "weight", label: "체중", unit: "kg" },
    { key: "skeletalMuscleMass", label: "골격근량", unit: "kg" },
    { key: "bodyFatMass", label: "체지방량", unit: "kg" },
    { key: "bodyFatPercentage", label: "체지방률", unit: "%" },
    { key: "bmi", label: "BMI", unit: "" },
    { key: "visceralFatLevel", label: "내장지방", unit: "" },
];

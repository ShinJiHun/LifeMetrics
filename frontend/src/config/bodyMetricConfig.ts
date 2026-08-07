import type { MetricKey } from "@/types/MetricKey";
export const bodyMetricConfig: Record<MetricKey, { label: string; unit: string }> = {
    weight: { label: "체중", unit: "kg" },
    skeletalMuscleMass: { label: "골격근량", unit: "kg" },
    bodyFatMass: { label: "체지방량", unit: "kg" },
    fatFreeMass: { label: "제지방량", unit: "kg" },
    bodyFatPercentage: { label: "체지방률", unit: "%" },
    bmi: { label: "BMI", unit: "" },
    visceralFatLevel: { label: "내장지방", unit: "" },
};
// src/config/bodyMetricConfig.ts
import type { MetricKey } from "@/types/MetricKey";

export const bodyMetricConfig: Record<
    MetricKey,
    { label: string; unit: string }
> = {
    weight: { label: "체중", unit: "kg" },
    skeletal_muscle_mass: { label: "골격근량", unit: "kg" },
    body_fat_mass: { label: "체지방량", unit: "kg" },
    body_fat_percentage: { label: "체지방률", unit: "%" },
    bmi: { label: "BMI", unit: "" },
    visceral_fat_level: { label: "내장지방", unit: "" },
};

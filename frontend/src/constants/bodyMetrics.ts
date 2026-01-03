import type { MetricKey } from "@/types/MetricKey";

export const BODY_METRICS: {
    key: MetricKey;
    label: string;
    unit: string;
}[] = [
    { key: "weight", label: "체중", unit: "kg" },
    { key: "skeletal_muscle_mass", label: "골격근량", unit: "kg" },
    { key: "body_fat_mass", label: "체지방량", unit: "kg" },
    { key: "body_fat_percentage", label: "체지방률", unit: "%" },
    { key: "bmi", label: "BMI", unit: "" },
    { key: "visceral_fat_level", label: "내장지방", unit: "" },
];

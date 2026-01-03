export type BodyStatus = "normal" | "warning" | "danger";

export const BODY_THRESHOLDS = {
    weight: {
        normal: (v: number) => v < 85,
        warning: (v: number) => v >= 85 && v < 90,
        danger: (v: number) => v >= 90,
    },
    body_fat_percentage: {
        normal: (v: number) => v < 25,
        warning: (v: number) => v >= 25 && v < 30,
        danger: (v: number) => v >= 30,
    },
    skeletal_muscle_mass: {
        normal: (v: number) => v >= 34,
        warning: (v: number) => v >= 32 && v < 34,
        danger: (v: number) => v < 32,
    },
    bmi: {
        normal: (v: number) => v < 23,
        warning: (v: number) => v >= 23 && v < 25,
        danger: (v: number) => v >= 25,
    },
};

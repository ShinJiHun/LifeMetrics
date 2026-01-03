// types/BodyApiRecord.ts
export interface BodyApiRecord {
    record_date: string;

    weight: number;
    skeletal_muscle_mass: number;
    skeletal_muscle_mass_delta?: number | null;

    body_fat_percentage: number;
    body_fat_percentage_delta?: number | null;

    body_fat_mass: number;
    body_fat_mass_delta?: number | null;

    bmi: number;
    visceral_fat_level: number;

    is_measured: boolean;
}

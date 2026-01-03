// types/RawBodyRecord.ts
export interface RawBodyRecord {
    record_date: string;

    weight?: number;
    skeletal_muscle_mass?: number;
    body_fat_mass?: number;
    body_fat_percentage?: number;
    bmi?: number;
    visceral_fat_level?: number;

    waist_circumference?: number;
    thigh_circumference?: number;
    arm_circumference?: number;
    left_calf_circumference?: number;
    right_calf_circumference?: number;

    is_measured?: boolean;
}

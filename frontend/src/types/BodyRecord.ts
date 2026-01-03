// types/BodyRecord.ts
export interface BodyRecord {
  recordDate: string;

  weight: number;
  skeletalMuscleMass: number;
  bodyFatPercentage: number;
  bodyFatMass: number;
  bmi: number;
  visceralFatLevel: number;

  isMeasured: boolean;
}

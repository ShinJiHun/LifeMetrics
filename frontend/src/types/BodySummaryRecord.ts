export interface BodySummaryRecord {
    recordDate: string;

    weight: number;
    weightDelta: number | null;

    skeletalMuscleMass: number;
    skeletalMuscleMassDelta: number | null;

    bodyFatMass: number;
    bodyFatPercentage: number;
    bodyFatPercentageDelta: number | null;

    bmi: number | null;
    visceralFatLevel: number | null;

    isMeasured: boolean | null;
    measurementType: string | null;  // 추가

    rawLlmJson: string | null;
}

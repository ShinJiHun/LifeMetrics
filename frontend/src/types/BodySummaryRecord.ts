export interface BodySummaryRecord {
    id: number;
    recordDate: string;

    weight: number;
    weightDelta: number | null;

    skeletalMuscleMass: number;
    skeletalMuscleMassDelta: number | null;

    bodyFatMass: number;

    /** 제지방량 = 체중 - 체지방량. 인바디 기록지 값과 일치한다. */
    fatFreeMass: number | null;
    fatFreeMassDelta: number | null;

    bodyFatPercentage: number;
    bodyFatPercentageDelta: number | null;

    bmi: number | null;
    visceralFatLevel: number | null;

    isMeasured: boolean | null;
    measurementType: string | null;  // 추가

    rawLlmJson: string | null;
}

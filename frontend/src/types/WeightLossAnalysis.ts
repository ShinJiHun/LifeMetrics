export interface WeightLossScenario {
    label: string;
    basis: string;
    kcalPerHour: number;
    hoursPerKg: number;
    kmPerKg: number;
    primary: boolean;
}

export interface WeightLossAnalysis {
    // 체성분 (최신 인바디)
    recordDate: string;
    weight: number;
    bodyFatMass: number;
    bodyFatPercentage: number;
    fatFreeMass: number;
    basalMetabolicRate: number;
    bmrEstimated: boolean;
    recordAgeDays: number;

    // 라이딩 소비 (파워미터 기록)
    rideCount: number;
    rideHours: number;
    rideKm: number;
    avgSpeedKmh: number;
    kjPerHour: number;

    scenarios: WeightLossScenario[];

    // 라이딩과 무관하게 생기는 일상 적자
    dailyDeficitKcal: number;
    dailyDeficitDaysPerKg: number;
}

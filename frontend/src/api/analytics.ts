export interface ZoneSetting {
    ftp: number;
    maxHr: number;
    ftpEstimated: boolean;
    maxHrEstimated: boolean;
}

export interface ZoneTime {
    zone: string;
    name: string;
    min: number;
    max: number | null;
    seconds: number;
    percent: number;
}

export interface CurvePoint {
    seconds: number;
    label: string;
    watts: number;
}

export interface Interval {
    startKm: number;
    durationSec: number;
    avgPower: number;
    maxPower: number;
    avgHr: number | null;
    avgCadence: number | null;
    zone: string;
}

export interface StreamPoint {
    km: number;
    speed: number | null;
    power: number | null;
    cadence: number | null;
    hr: number | null;
    altitude: number | null;
}

export interface ActivityAnalytics {
    zoneSetting: ZoneSetting;
    normalizedPower: number | null;
    avgPower: number | null;
    intensityFactor: number | null;
    trainingStress: number | null;
    variabilityIndex: number | null;
    workKj: number | null;
    wattsPerKg: number | null;
    powerZones: ZoneTime[];
    hrZones: ZoneTime[];
    powerCurve: CurvePoint[];
    intervals: Interval[];
    streams: StreamPoint[];
}

export async function fetchActivityAnalytics(activityId: number | string): Promise<ActivityAnalytics> {
    const res = await fetch(`/api/activity/${activityId}/analytics`);
    if (!res.ok) throw new Error(`분석 데이터 로드 실패 (${res.status})`);
    return res.json();
}

export async function saveZoneSetting(ftp: number | null, maxHr: number | null): Promise<ZoneSetting> {
    const res = await fetch(`/api/rider/zone-setting?userId=1`, {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({ftp, maxHr}),
    });
    if (!res.ok) throw new Error(`존 설정 저장 실패 (${res.status})`);
    return res.json();
}

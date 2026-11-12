// src/api/power.ts
//
// 파워 프로필: 파워미터 기록으로 만든 파워 커브·라이더 성향·피로 저항.

const API_BASE = import.meta.env.VITE_API_BASE || "";

export interface CurvePoint {
    seconds: number;
    label: string;
    watts: number;
    wkg: number;
    activityId: number | null;
    date: string | null;
    name: string | null;
    recentWatts: number;
}

export interface TypeScore {
    key: string;
    label: string;
    wkg: number;
    score: number;
}

export interface RiderType {
    type: string;
    label: string;
    description: string;
    scores: TypeScore[];
}

export interface ZoneTime {
    zone: string;
    label: string;
    fromW: number;
    toW: number | null;
    seconds: number;
    pct: number;
}

export interface WorkBand {
    label: string;
    fromKj: number;
    best5m: number;
    best20m: number;
}

export interface LongRide {
    activityId: number;
    name: string | null;
    date: string;
    km: number;
    hours: number;
    np: number;
    kj: number;
    fadePct: number | null;
    hrDriftPct: number | null;
    hourly: { hour: number; power: number; hr: number | null }[];
}

export interface PowerProfile {
    meterSince: string;
    rideCount: number;
    totalHours: number;
    totalKm: number;
    weightKg: number;
    ftpSetting: number;
    ftpSettingEstimated: boolean;
    estimatedFtp: number;
    recentEstimatedFtp: number;
    curve: CurvePoint[];
    riderType: RiderType;
    zones: ZoneTime[];
    durability: {
        bands: WorkBand[];
        retention5mPct: number | null;
        retention20mPct: number | null;
        avgFadePct: number | null;
        longRides: LongRide[];
    };
}

export async function fetchPowerProfile(userId = 1): Promise<PowerProfile> {
    const res = await fetch(`${API_BASE}/api/power/profile?userId=${userId}`);
    if (!res.ok) throw new Error("파워 프로필을 불러오지 못했습니다.");
    return res.json();
}

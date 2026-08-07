const API_BASE = import.meta.env.VITE_API_BASE || "";

export interface GearContext {
    bikeId: number;
    bikeLabel?: string;
    chainring?: string;
    cassette?: string;
    tire?: string;
    etc?: Record<string, string>;
}

export interface Activity {
    id: number;
    filename: string;
    startTime: string;
    endTime: string;
    totalDistance: number;
    movingTime: number;
    elapsedTime: number;
    avgSpeed: number;
    maxSpeed: number;
    totalAscent: number;
    totalDescent: number;
    polyline?: string;
    avgHeartRate?: number;
    maxHeartRate?: number;
    avgPower?: number;
    avgCadence?: number;
    calories?: number;
    gearContext?: GearContext;
}

export async function fetchActivities(userId = 1): Promise<Activity[]> {
    const res = await fetch(`${API_BASE}/api/activity/list?userId=${userId}`);
    if (!res.ok) throw new Error("Failed to fetch activities");
    return res.json();
}

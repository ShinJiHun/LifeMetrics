const API_BASE = import.meta.env.VITE_API_BASE || "";

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
    gearName: string;
    polyline?: string;
    avgHeartRate?: number;
    maxHeartRate?: number;
    avgPower?: number;
    avgCadence?: number;
    calories?: number;
}

export async function fetchActivities(userId = 1): Promise<Activity[]> {
    const res = await fetch(`${API_BASE}/api/activity/list?userId=${userId}`);
    if (!res.ok) throw new Error("Failed to fetch activities");
    return res.json();
}

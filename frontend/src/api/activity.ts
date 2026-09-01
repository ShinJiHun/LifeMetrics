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
    rideType?: string;
    permanentNo?: string;
}

export async function fetchActivities(userId = 1): Promise<Activity[]> {
    const res = await fetch(`${API_BASE}/api/activity/list?userId=${userId}`);
    if (!res.ok) throw new Error("Failed to fetch activities");
    return res.json();
}

// parentId 활동에 targetId 활동을 물리적으로 병합한다. targetId는 병합 후 삭제된다.
export async function mergeActivities(parentId: number, targetId: number): Promise<void> {
    const res = await fetch(
        `${API_BASE}/api/activities/merge?parent_id=${parentId}&target_id=${targetId}`,
        {method: "POST"}
    );
    if (!res.ok) throw new Error(await res.text() || "병합에 실패했습니다.");
}

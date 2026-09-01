const API_BASE = import.meta.env.VITE_API_BASE || "";

// 퍼머넌트 코스 × gpx 파일 1개 조합. 폴더 안에 gpx가 여러 개(본코스/Plan B 등)면
// permanentNo가 같은 행이 여러 개 온다. gpx가 아예 없으면 gpxFileName이 null인 행 1개만 온다.
export interface PermanentCourse {
    permanentNo: string;   // 예: "PT-01"
    name: string;
    distanceKm?: number;
    timeLimitHm?: string;  // "HH:MM"
    region?: string;
    gpxFileName?: string | null;
    gpxLabel?: string | null;
    polyline?: string | null;   // 코스 대표 gpx를 인코딩한 경로 (DB에 미리 저장된 값, 코스당 1개)
}

export async function fetchPermanentCourses(): Promise<PermanentCourse[]> {
    const res = await fetch(`${API_BASE}/api/permanents`);
    if (!res.ok) throw new Error("퍼머넌트 코스 목록을 불러오지 못했습니다.");
    return res.json();
}

export async function fetchPermanentGpx(permanentNo: string, gpxFileName: string): Promise<string> {
    const res = await fetch(
        `${API_BASE}/api/permanents/${encodeURIComponent(permanentNo)}/gpx?file=${encodeURIComponent(gpxFileName)}`
    );
    if (!res.ok) throw new Error("퍼머넌트 GPX를 불러오지 못했습니다.");
    return res.text();
}

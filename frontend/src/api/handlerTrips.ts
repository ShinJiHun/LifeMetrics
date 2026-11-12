import api from "@/lib/axios";

/** 캡처에서 읽은 값 + 사용자가 고친 값. 서버 HandlerTripService.Draft 와 1:1. */
export interface TripDraft {
    tripDate: string | null; // YYYY-MM-DD
    startTime: string | null; // HH:mm
    endTime: string | null;
    serviceType: string | null;
    carModel: string | null;
    carNumber: string | null;
    fare: number | null;
    distanceKm: number | null;
    durationMin: number | null;
    originName: string | null;
    originAddress: string | null;
    destName: string | null;
    destAddress: string | null;
    memo: string | null; // 핸들 내용 / 메모
    handleCode: string | null;
}

export interface Place {
    query: string;
    address: string;
    placeName: string | null;
    lat: number;
    lon: number;
    source: "address" | "keyword";
}

export interface Route {
    polyline: string;
    distanceM: number;
    durationS: number | null;
    source: "kakao" | "osrm" | "straight";
}

export interface TripPreview {
    draft: TripDraft;
    origin: Place | null;
    dest: Place | null;
    route: Route | null;
    duplicateId: number | null;
    warnings: string[];
    raw: string | null;
}

export interface HandlerTrip {
    id: number;
    tripDate: string;
    startTime: string | null;
    endTime: string | null;
    serviceType: string | null;
    carModel: string | null;
    carNumber: string | null;
    fare: number | null;
    screenDistanceKm: number | null;
    durationMin: number | null;
    originName: string | null;
    originAddress: string | null;
    originLat: number | null;
    originLon: number | null;
    destName: string | null;
    destAddress: string | null;
    destLat: number | null;
    destLon: number | null;
    routeSource: string | null;
    routeDistanceM: number | null;
    routeDurationS: number | null;
    polyline: string | null;
    hasImage: boolean;
    memo: string | null;
    handleCode: string | null;
    createdAt: string | null;
}

const BASE = "/human/handler-trips";

export async function fetchTrips(): Promise<HandlerTrip[]> {
    return (await api.get<HandlerTrip[]>(BASE)).data;
}

/** 캡처 1장 판독 + 위치·경로 미리보기 (저장 안 함). 화면에 연도가 없어 year 를 같이 보낸다. */
export async function extractTrip(file: File, year: number): Promise<TripPreview> {
    const form = new FormData();
    form.append("file", file);
    form.append("year", String(year));
    const res = await api.post<TripPreview>(`${BASE}/extract`, form, {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: 120_000,
    });
    return res.data;
}

/** 주소를 고친 뒤 위치·경로만 다시 찾기. */
export async function resolveTrip(draft: TripDraft): Promise<TripPreview> {
    return (await api.post<TripPreview>(`${BASE}/resolve`, draft)).data;
}

export async function saveTrip(draft: TripDraft, file: File | null, raw: string | null): Promise<HandlerTrip> {
    const form = new FormData();
    form.append("data", JSON.stringify(draft));
    if (file) form.append("file", file);
    if (raw) form.append("raw", raw);
    const res = await api.post<HandlerTrip>(BASE, form, {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: 60_000,
    });
    return res.data;
}

export async function deleteTrip(id: number): Promise<void> {
    await api.delete(`${BASE}/${id}`);
}

export function tripImageUrl(id: number): string {
    return `/api${BASE}/${id}/image`;
}

import api from "@/lib/axios";

export type RandoStatus = "FINISHED" | "DNF";

export interface RandonneuringResult {
    id: number;
    eventDate: string;             // YYYY-MM-DD
    organizer: string | null;
    eventType: "BREVET" | "PERMANENT" | "FLECHE" | "POPULAIRE";
    eventName: string;
    permanentNo: string | null;
    distanceKm: number | null;
    status: RandoStatus;
    ridingTimeSec: number | null;  // DNF 는 null
    activityCoreId: number | null; // 연결된 GPS 라이딩
    homologationNo: string | null;
    notes: string | null;
}

export interface RandonneuringSummary {
    career: string | null;
    items: RandonneuringResult[];
}

export interface ImportResult {
    total: number;
    inserted: number;
    updated: number;
    skipped: number;
    linked: number;
    tagged: number;
    warnings: string[];
}

export const fetchRandonneuring = () =>
    api.get<RandonneuringSummary>("/randonneuring/results").then((r) => r.data);

/** KORA 기록 API 응답 JSON 을 그대로 보낸다 (관리자 전용). */
export const importKora = (json: unknown) =>
    api.post<ImportResult>("/randonneuring/import", json).then((r) => r.data);

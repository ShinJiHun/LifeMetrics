// 장비 변경 계획 — 자전거 소모품 교체 주기

export type PartType =
    | "CHAIN" | "TIRE_FRONT" | "TIRE_REAR" | "CASSETTE" | "CHAINRING"
    | "BRAKE_PAD" | "BAR_TAPE" | "CABLE" | "SEALANT" | "OTHER";

/** 종류별 표시 이름과 일반적인 교체 주기(새로 추가할 때 기본값으로 채움) */
export const PART_TYPES: { type: PartType; label: string; icon: string; km?: number; months?: number }[] = [
    {type: "CHAIN", label: "체인", icon: "⛓️", km: 3000},
    {type: "TIRE_REAR", label: "뒷타이어", icon: "🛞", km: 4000},
    {type: "TIRE_FRONT", label: "앞타이어", icon: "🛞", km: 6000},
    {type: "CASSETTE", label: "카세트", icon: "⚙️", km: 10000},
    {type: "CHAINRING", label: "체인링", icon: "⚙️", km: 20000},
    {type: "BRAKE_PAD", label: "브레이크 패드", icon: "🛑", km: 3000},
    {type: "BAR_TAPE", label: "바테이프", icon: "🎗️", months: 12},
    {type: "CABLE", label: "케이블·하우징", icon: "🧵", months: 12},
    {type: "SEALANT", label: "실란트", icon: "💧", months: 4},
    {type: "OTHER", label: "기타", icon: "🔩"},
];

export const partMeta = (t: PartType) => PART_TYPES.find((p) => p.type === t) ?? PART_TYPES[PART_TYPES.length - 1];

export type PartStatus = "OK" | "SOON" | "OVERDUE" | "NONE" | "REMOVED";

export interface PartUsage {
    id: number;
    bikeId: number;
    partType: PartType;
    name: string | null;
    installedOn: string;
    removedOn: string | null;
    initialKm: number;
    intervalKm: number | null;
    intervalMonths: number | null;
    note: string | null;
    usedKm: number;
    usedDays: number;
    ratio: number | null;
    status: PartStatus;
    remainingKm: number | null;
    expectedReplaceOn: string | null;
}

export interface GearKm {
    teeth: number;
    km: number;
}

export interface BikePlan {
    bikeId: number;
    bikeName: string;
    retired: boolean;
    totalKm: number;
    recentKmPerDay: number;
    parts: PartUsage[];
    history: PartUsage[];
    /** 사용 중인 체인링 부품 장착일. null 이면 전체 기간 */
    frontSince: string | null;
    frontGears: GearKm[];
    /** 사용 중인 카세트 부품 장착일. null 이면 전체 기간 */
    rearSince: string | null;
    rearGears: GearKm[];
}

export interface PartInput {
    id?: number;
    bikeId: number;
    partType: PartType;
    name: string | null;
    installedOn: string;
    removedOn: string | null;
    initialKm: number;
    intervalKm: number | null;
    intervalMonths: number | null;
    note: string | null;
}

async function errorOf(res: Response, fallback: string): Promise<Error> {
    try {
        const body = await res.json();
        if (body?.error) return new Error(body.error);
    } catch { /* 본문 없음 */ }
    return new Error(`${fallback} (${res.status})`);
}

export async function fetchGearPlan(includeRetired = false): Promise<BikePlan[]> {
    const res = await fetch(`/api/bike-parts/plan?includeRetired=${includeRetired}`);
    if (!res.ok) throw await errorOf(res, "장비 계획을 불러오지 못했습니다");
    return res.json();
}

export async function savePart(part: PartInput): Promise<void> {
    const res = await fetch(part.id ? `/api/bike-parts/${part.id}` : `/api/bike-parts`, {
        method: part.id ? "PUT" : "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(part),
    });
    if (!res.ok) throw await errorOf(res, "저장 실패");
}

/** 순정 소모품 일괄 등록(구입일 기준). 등록된 개수를 돌려준다. */
export async function registerStockParts(bikeId: number): Promise<number> {
    const res = await fetch(`/api/bike-parts/bike/${bikeId}/stock`, {method: "POST"});
    if (!res.ok) throw await errorOf(res, "순정 소모품 등록 실패");
    return (await res.json()).length;
}

export async function replacePart(id: number, body: { date: string; name: string | null; note: string | null }): Promise<void> {
    const res = await fetch(`/api/bike-parts/${id}/replace`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
    });
    if (!res.ok) throw await errorOf(res, "교체 기록 실패");
}

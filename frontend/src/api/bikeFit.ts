export interface BikeFit {
    id?: number;
    bikeId?: number;
    fitDate: string;            // YYYY-MM-DD, 이 날부터 적용
    fitter: string | null;
    saddleHeightMm: number | null;
    saddleSetbackMm: number | null;
    saddleTiltDeg: number | null;
    saddleModel: string | null;
    reachMm: number | null;
    dropMm: number | null;
    stemLengthMm: number | null;
    stemAngleDeg: number | null;
    spacerMm: number | null;
    handlebarWidthMm: number | null;
    crankLengthMm: number | null;
    cleatLeft: string | null;
    cleatRight: string | null;
    notes: string | null;
}

export type FitNumberKey =
    "saddleHeightMm" | "saddleSetbackMm" | "saddleTiltDeg" | "reachMm" | "dropMm" |
    "stemLengthMm" | "stemAngleDeg" | "spacerMm" | "handlebarWidthMm" | "crankLengthMm";

export const FIT_NUMBER_FIELDS: { key: FitNumberKey; label: string; unit: string; hint?: string }[] = [
    {key: "saddleHeightMm", label: "안장 높이", unit: "mm", hint: "BB 중심 ~ 안장 상단"},
    {key: "saddleSetbackMm", label: "안장 셋백", unit: "mm", hint: "BB 수직선 ~ 안장 코"},
    {key: "saddleTiltDeg", label: "안장 각도", unit: "°", hint: "+ 코 들림 / − 코 내림"},
    {key: "reachMm", label: "리치", unit: "mm", hint: "안장 코 ~ 핸들바 중심"},
    {key: "dropMm", label: "드롭", unit: "mm", hint: "안장 상단 ~ 핸들바 상단 높이차"},
    {key: "stemLengthMm", label: "스템 길이", unit: "mm"},
    {key: "stemAngleDeg", label: "스템 각도", unit: "°"},
    {key: "spacerMm", label: "스페이서", unit: "mm", hint: "스템 아래 합계"},
    {key: "handlebarWidthMm", label: "핸들바 폭", unit: "mm"},
    {key: "crankLengthMm", label: "크랭크 길이", unit: "mm"},
];

export const emptyFit = (): BikeFit => ({
    fitDate: new Date().toISOString().slice(0, 10),
    fitter: null, saddleHeightMm: null, saddleSetbackMm: null, saddleTiltDeg: null, saddleModel: null,
    reachMm: null, dropMm: null, stemLengthMm: null, stemAngleDeg: null, spacerMm: null,
    handlebarWidthMm: null, crankLengthMm: null, cleatLeft: null, cleatRight: null, notes: null,
});

export async function fetchBikeFits(bikeId: number | string): Promise<BikeFit[]> {
    const res = await fetch(`/api/bikes/${bikeId}/fits`);
    if (!res.ok) throw new Error(`피팅 기록을 불러오지 못했습니다 (${res.status})`);
    return res.json();
}

export async function saveBikeFit(bikeId: number | string, fit: BikeFit): Promise<BikeFit> {
    const res = await fetch(fit.id ? `/api/bike-fits/${fit.id}` : `/api/bikes/${bikeId}/fits`, {
        method: fit.id ? "PUT" : "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(fit),
    });
    if (!res.ok) throw new Error(`저장 실패 (${res.status})`);
    return res.json();
}

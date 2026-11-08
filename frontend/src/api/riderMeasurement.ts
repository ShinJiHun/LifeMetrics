export type ArchType = "LOW" | "MEDIUM" | "HIGH";

export interface RiderMeasurement {
    id?: number;
    measureDate: string;            // YYYY-MM-DD
    measuredBy: string | null;
    heightCm: number | null;
    inseamMm: number | null;
    torsoMm: number | null;
    armMm: number | null;
    shoulderWidthMm: number | null;
    sitBoneWidthMm: number | null;
    footLengthLeftMm: number | null;
    footLengthRightMm: number | null;
    footWidthLeftMm: number | null;
    footWidthRightMm: number | null;
    archType: ArchType | null;
    shoeSizeMm: number | null;
    shoeSizeEu: number | null;
    shoeModel: string | null;
    insole: string | null;
    flexibility: string | null;
    notes: string | null;
}

export type MeasureNumberKey =
    "heightCm" | "inseamMm" | "torsoMm" | "armMm" | "shoulderWidthMm" | "sitBoneWidthMm" |
    "footLengthLeftMm" | "footLengthRightMm" | "footWidthLeftMm" | "footWidthRightMm" |
    "shoeSizeMm" | "shoeSizeEu";

export type MeasureTextKey = "measuredBy" | "shoeModel" | "insole" | "flexibility" | "notes";

export interface MeasureField { key: MeasureNumberKey; label: string; unit: string; hint?: string }

export const BODY_FIELDS: MeasureField[] = [
    {key: "heightCm", label: "키", unit: "cm"},
    {key: "inseamMm", label: "인심", unit: "mm", hint: "맨발로 바닥 ~ 사타구니"},
    {key: "torsoMm", label: "몸통 길이", unit: "mm", hint: "사타구니 ~ 흉골 상단"},
    {key: "armMm", label: "팔 길이", unit: "mm", hint: "어깨 끝 ~ 손목"},
    {key: "shoulderWidthMm", label: "어깨 폭", unit: "mm", hint: "양쪽 견봉 사이"},
    {key: "sitBoneWidthMm", label: "좌골 폭", unit: "mm", hint: "안장 폭 고를 때"},
];

export const FOOT_FIELDS: MeasureField[] = [
    {key: "footLengthLeftMm", label: "발 길이 (왼쪽)", unit: "mm"},
    {key: "footLengthRightMm", label: "발 길이 (오른쪽)", unit: "mm"},
    {key: "footWidthLeftMm", label: "발볼 (왼쪽)", unit: "mm"},
    {key: "footWidthRightMm", label: "발볼 (오른쪽)", unit: "mm"},
    {key: "shoeSizeMm", label: "신발 사이즈", unit: "mm", hint: "한국 기준"},
    {key: "shoeSizeEu", label: "신발 사이즈", unit: "EU"},
];

export const ARCH_LABEL: Record<ArchType, string> = {LOW: "낮음 (평발 쪽)", MEDIUM: "보통", HIGH: "높음"};

export const emptyMeasurement = (): RiderMeasurement => ({
    measureDate: new Date().toISOString().slice(0, 10),
    measuredBy: null, heightCm: null, inseamMm: null, torsoMm: null, armMm: null,
    shoulderWidthMm: null, sitBoneWidthMm: null, footLengthLeftMm: null, footLengthRightMm: null,
    footWidthLeftMm: null, footWidthRightMm: null, archType: null, shoeSizeMm: null, shoeSizeEu: null,
    shoeModel: null, insole: null, flexibility: null, notes: null,
});

export async function fetchRiderMeasurements(): Promise<RiderMeasurement[]> {
    const res = await fetch("/api/rider-measurements");
    if (!res.ok) throw new Error(`신체 치수를 불러오지 못했습니다 (${res.status})`);
    return res.json();
}

export async function saveRiderMeasurement(m: RiderMeasurement): Promise<RiderMeasurement> {
    const res = await fetch(m.id ? `/api/rider-measurements/${m.id}` : "/api/rider-measurements", {
        method: m.id ? "PUT" : "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(m),
    });
    if (!res.ok) throw new Error(`저장 실패 (${res.status})`);
    return res.json();
}

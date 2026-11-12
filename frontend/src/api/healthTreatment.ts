import api from "@/lib/axios";

/** 치료·부상 기록 1건. 서버 HealthTreatment 와 1:1. endDate 가 null 이면 치료 중. */
export interface HealthTreatment {
    id?: number;
    name: string;
    diagnosisCode: string | null;
    bodyPart: string | null;
    startDate: string; // "YYYY-MM-DD"
    endDate: string | null;
    restrictions: string | null;
    notes: string | null;
    sourceFile: string | null;
}

export function emptyHealthTreatment(): HealthTreatment {
    return {
        name: "", diagnosisCode: null, bodyPart: null, startDate: "", endDate: null,
        restrictions: null, notes: null, sourceFile: null,
    };
}

export async function fetchHealthTreatments(userId = 1): Promise<HealthTreatment[]> {
    const res = await api.get<HealthTreatment[]>("/body/treatments", { params: { userId } });
    return res.data;
}

/** 진단서·소견서(PDF·사진) → AI 추출 결과(저장 안 됨). 폼 prefill 용. 날짜를 못 읽으면 startDate 가 null. */
export async function extractTreatmentDocument(file: File): Promise<Omit<HealthTreatment, "startDate"> & { startDate: string | null }> {
    const form = new FormData();
    form.append("file", file);
    const res = await api.post("/body/treatments/extract", form, {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: 120_000,
    });
    return res.data;
}

export async function createHealthTreatment(payload: HealthTreatment, userId = 1): Promise<HealthTreatment> {
    const res = await api.post<HealthTreatment>("/body/treatments", payload, { params: { userId } });
    return res.data;
}

export async function updateHealthTreatment(id: number, payload: HealthTreatment): Promise<HealthTreatment> {
    const res = await api.put<HealthTreatment>(`/body/treatments/${id}`, payload);
    return res.data;
}

export async function deleteHealthTreatment(id: number): Promise<void> {
    await api.delete(`/body/treatments/${id}`);
}

// ── 진료·처방 내역 (처방전·약제비 영수증·진료비 세부내역서·처방조제내역) ──

/** 진료·처방 항목. category: 진찰 / 검사 / 영상 / 주사 / 물리치료 / 처치 / 약 / 재료 / 기타 */
export interface TreatmentVisitItem {
    category: string;
    name: string;
    code?: string;
    dose?: string;
    timesPerDay?: number;
    days?: number;
    drugClass?: string;
    amount?: number;
}

/** 치료 1건에 딸린 진료·처방 1회분 (날짜별). */
export interface TreatmentVisit {
    id?: number;
    treatmentId: number;
    visitDate: string; // YYYY-MM-DD
    institution: string | null;
    department: string | null;
    docType: string | null;
    diagnosis: string | null;
    items: TreatmentVisitItem[];
    totalAmount: number | null;
    patientAmount: number | null;
    summary: string | null;
    sourceFile: string | null;
}

export async function fetchTreatmentVisits(userId = 1): Promise<TreatmentVisit[]> {
    const res = await api.get<TreatmentVisit[]>("/body/treatments/visits", { params: { userId } });
    return res.data;
}

/** 문서 → 날짜별 진료 미리보기 (저장 안 됨). 세부내역서처럼 여러 날짜면 여러 건. */
export async function extractTreatmentVisits(treatmentId: number, file: File): Promise<TreatmentVisit[]> {
    const form = new FormData();
    form.append("file", file);
    const res = await api.post<TreatmentVisit[]>(`/body/treatments/${treatmentId}/visits/extract`, form, {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: 240_000,
    });
    return res.data;
}

export async function saveTreatmentVisits(treatmentId: number, visits: TreatmentVisit[]): Promise<TreatmentVisit[]> {
    const res = await api.post<TreatmentVisit[]>(`/body/treatments/${treatmentId}/visits`, visits);
    return res.data;
}

export async function deleteTreatmentVisit(visitId: number): Promise<void> {
    await api.delete(`/body/treatments/visits/${visitId}`);
}

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

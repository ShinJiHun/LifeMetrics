import api from "@/lib/axios";

/** 치료·부상 기록 1건. 서버 HealthTreatment 와 1:1. endDate 가 null 이면 치료 중. */
export interface HealthTreatment {
    id?: number;
    name: string;
    bodyPart: string | null;
    startDate: string; // "YYYY-MM-DD"
    endDate: string | null;
    restrictions: string | null;
    notes: string | null;
}

export function emptyHealthTreatment(): HealthTreatment {
    return { name: "", bodyPart: null, startDate: "", endDate: null, restrictions: null, notes: null };
}

export async function fetchHealthTreatments(userId = 1): Promise<HealthTreatment[]> {
    const res = await api.get<HealthTreatment[]>("/body/treatments", { params: { userId } });
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

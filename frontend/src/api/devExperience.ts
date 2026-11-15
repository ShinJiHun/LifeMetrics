import api from "@/lib/axios";

export type Visibility = "PUBLIC" | "PRIVATE";

export interface DevExperience {
    id?: number;
    project: string;
    happenedOn: string | null;   // YYYY-MM-DD
    title: string;
    situation: string;
    cause: string | null;
    decision: string | null;
    result: string | null;
    lesson: string | null;
    tags: string | null;          // 쉼표 구분
    visibility: Visibility;
    sourceRef: string | null;
}

export const emptyExperience = (project = "LifeMetrics"): DevExperience => ({
    project,
    happenedOn: null,
    title: "",
    situation: "",
    cause: null,
    decision: null,
    result: null,
    lesson: null,
    tags: null,
    visibility: "PRIVATE",
    sourceRef: null,
});

export const fetchExperiences = () => api.get<DevExperience[]>("/persona/experiences").then((r) => r.data);

export const saveExperience = (e: DevExperience) =>
    (e.id ? api.put<DevExperience>(`/persona/experiences/${e.id}`, e) : api.post<DevExperience>("/persona/experiences", e))
        .then((r) => r.data);

export const deleteExperience = (id: number) => api.delete(`/persona/experiences/${id}`);

/** 커밋 기록·메모 → 카드 초안 (저장하지 않음, 모두 비공개로 시작). */
export const draftExperiences = (project: string, notes: string) =>
    api.post<DevExperience[]>("/persona/experiences/draft", { project, notes }, { timeout: 120_000 }).then((r) => r.data);

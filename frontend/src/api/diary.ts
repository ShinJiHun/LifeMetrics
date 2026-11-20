import api from "@/lib/axios";

export type NoteSource = "app" | "cli" | "manual";

export interface DiaryNote {
    id: number;
    noteDate: string;
    source: NoteSource;
    fileName: string | null;
    content: string;
    createdAt: string;
}

export interface DiaryEntry {
    id: number;
    entryDate: string;
    kind: "diary" | "book";
    title: string;
    content: string;
    warnings: string[];
    status: "draft" | "published";
    edited: boolean;
    postId: number | null;
    generatedAt: string;
    publishedAt: string | null;
}

export interface DiaryDay {
    date: string;
    notes: DiaryNote[];
    diary: DiaryEntry | null;
    book: DiaryEntry | null;
    records: Record<string, unknown>;
}

export interface DiarySubMenu {
    id: number;
    category: string;
    name: string;
}

export interface DiaryRecent {
    date: string;
    notes: number;
    diary: "draft" | "published" | null;
}

export const fetchDiaryDay = (date: string) => api.get<DiaryDay>(`/diary/${date}`).then((r) => r.data);
export const fetchDiaryRecent = () => api.get<DiaryRecent[]>("/diary/recent").then((r) => r.data);
export const fetchDiarySubMenus = () =>
    api.get<{ subs: DiarySubMenu[]; diaryDefault: number }>("/diary/sub-menus").then((r) => r.data);

export const addDiaryNote = (date: string, source: NoteSource, content: string, fileName?: string) =>
    api.post<DiaryNote>(`/diary/${date}/notes`, { source, content, fileName: fileName ?? null }).then((r) => r.data);
export const deleteDiaryNote = (id: number) => api.delete(`/diary/notes/${id}`);

/** AI 호출이라 오래 걸린다. */
export const generateDiary = (date: string) =>
    api.post<DiaryDay>(`/diary/${date}/generate`, null, { timeout: 180_000 }).then((r) => r.data);
export const updateDiaryEntry = (id: number, title: string, content: string) =>
    api.put<DiaryEntry>(`/diary/entries/${id}`, { title, content }).then((r) => r.data);
export const discardDiaryEntry = (id: number) => api.delete(`/diary/entries/${id}`);
export const publishDiaryEntry = (id: number, visibility: "public" | "private", subId?: number) =>
    api.post<DiaryEntry>(`/diary/entries/${id}/publish`, { visibility, subId: subId ?? null }).then((r) => r.data);

/** PT 운동일지 구글 시트 → 운동 기록 동기화 결과 (PtSheetSyncService.Result). */
export interface PtSheetSyncResult {
    syncedAt: string;
    sessionsImported: number;
    skippedSessionNos: string[];
    logs: number;
    sets: number;
    newCategories: string[];
    newItems: string[];
    filledNames: number;
    cardioRows: number | null;
    cardioNew: number;
    cardioUpdated: number;
    cardioDeleted: number;
    anomalies: string[];
    warnings: string[];
    touchedDates: string[];
}

/** 시트 받기 + DB 반영이라 수십 초 걸릴 수 있다. */
export const syncPtSheet = () =>
    api.post<PtSheetSyncResult>("/exercise/pt-sheet/sync", null, { timeout: 120_000 }).then((r) => r.data);

export const errorMessage =(e: unknown) => {
    const err = e as { response?: { data?: { message?: string; error?: string } }; message?: string };
    return err.response?.data?.message ?? err.response?.data?.error ?? err.message ?? "요청을 처리하지 못했습니다.";
};

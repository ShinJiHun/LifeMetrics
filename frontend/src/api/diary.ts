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
    lectures: Lecture[];
}

/** 강연 요약 (lecture_entry). 원본 메모 하나와 1:1, 메모를 지웠으면 sourceNoteId 는 null. */
export interface Lecture {
    id: number;
    sourceNoteId: number | null;
    noteFileName: string | null;
    lectureDate: string;
    title: string;
    speaker: string | null;
    content: string;
    tags: string[];
    warnings: string[];
    status: "draft" | "published";
    edited: boolean;
    postId: number | null;
    generatedAt: string;
    publishedAt: string | null;
}

export interface LectureScanResult {
    scanned: number;
    created: number;
    errors: string[];
}

export interface LectureFacets {
    tags: { name: string; count: number }[];
    speakers: { name: string; count: number }[];
}

export interface LectureFilter {
    tag?: string;
    speaker?: string;
    from?: string;
    to?: string;
}

/** 메모마다 AI 를 부를 수 있어 오래 걸린다. */
export const generateLectures = (date: string) =>
    api.post<LectureScanResult>(`/diary/${date}/lectures/generate`, null, { timeout: 300_000 }).then((r) => r.data);
export const summarizeNoteAsLecture = (noteId: number) =>
    api.post<Lecture>(`/diary/notes/${noteId}/lecture`, null, { timeout: 180_000 }).then((r) => r.data);
export const fetchLectures = (f: LectureFilter) => {
    const params = Object.fromEntries(Object.entries(f).filter(([, v]) => v));
    return api.get<Lecture[]>("/diary/lectures", { params }).then((r) => r.data);
};
export const fetchLectureFacets = () => api.get<LectureFacets>("/diary/lectures/facets").then((r) => r.data);
export const updateLecture = (id: number, title: string, speaker: string, content: string) =>
    api.put<Lecture>(`/diary/lectures/${id}`, { title, speaker, content }).then((r) => r.data);
export const setLectureTags = (id: number, tags: string[]) =>
    api.put<Lecture>(`/diary/lectures/${id}/tags`, { tags }).then((r) => r.data);
export const discardLecture = (id: number) => api.delete(`/diary/lectures/${id}`);
export const publishLecture = (id: number, visibility: "public" | "private", subId?: number) =>
    api.post<Lecture>(`/diary/lectures/${id}/publish`, { visibility, subId: subId ?? null }).then((r) => r.data);

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
    api.get<{ subs: DiarySubMenu[]; diaryDefault: number; lectureDefault: number | null }>("/diary/sub-menus").then((r) => r.data);

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

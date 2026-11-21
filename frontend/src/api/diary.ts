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

/** 강연 요약 (lecture_entry). 메모 하나에서 여러 개가 나올 수 있다. 메모를 지웠으면 sourceNoteId 는 null. */
export interface Lecture {
    id: number;
    sourceNoteId: number | null;
    noteFileName: string | null;
    /** note: 일기 메모에서 AI 가 찾음, manual: 직접 기록 */
    origin: "note" | "manual";
    lectureDate: string;
    lectureTime: string | null;
    title: string;
    speaker: string | null;
    venue: string | null;
    host: string | null;
    series: string | null;
    books: string[];
    memo: string | null;
    /** 직접 기록은 [요약 만들기] 전까지 빈 문자열 */
    content: string;
    files: LectureFile[];
    tags: string[];
    warnings: string[];
    status: "draft" | "published";
    edited: boolean;
    postId: number | null;
    generatedAt: string;
    publishedAt: string | null;
}

export interface LectureFile {
    id: number;
    kind: "text" | "pdf" | "image" | "audio";
    fileName: string;
    sizeBytes: number;
    /** ready: 요약에 씀, none: 글 없음, pending: 받아쓰기 전, transcribing: 받아쓰는 중, failed: 받아쓰기 실패 */
    textStatus: "ready" | "none" | "pending" | "transcribing" | "failed";
    textNote: string | null;
    textChars: number;
    createdAt: string;
}

/** 강연 정보 입력. date 가 비면 오늘. */
export interface LectureInfo {
    title: string;
    speaker: string;
    date: string;
    time: string;
    venue: string;
    host: string;
    series: string;
    books: string[];
    memo: string;
}

export const lectureInfoOf = (l: Lecture): LectureInfo => ({
    title: l.title,
    speaker: l.speaker ?? "",
    date: l.lectureDate,
    time: l.lectureTime ?? "",
    venue: l.venue ?? "",
    host: l.host ?? "",
    series: l.series ?? "",
    books: l.books ?? [],
    memo: l.memo ?? "",
});

export interface LectureNasFile {
    path: string;
    sizeBytes: number;
    modifiedAt: string;
}

export const createLecture = (info: LectureInfo) => api.post<Lecture>("/diary/lectures", info).then((r) => r.data);
export const updateLectureInfo = (id: number, info: LectureInfo) =>
    api.put<Lecture>(`/diary/lectures/${id}/info`, info).then((r) => r.data);
/** 받아쓰기가 길면 AI 호출이 오래 걸린다. */
export const summarizeLecture = (id: number) =>
    api.post<Lecture>(`/diary/lectures/${id}/summarize`, null, { timeout: 180_000 }).then((r) => r.data);
export const uploadLectureFiles = (id: number, files: File[]) => {
    const form = new FormData();
    for (const f of files) form.append("files", f, f.name.normalize("NFC"));
    return api
        .post<LectureFile[]>(`/diary/lectures/${id}/files`, form, { headers: { "Content-Type": "multipart/form-data" }, timeout: 600_000 })
        .then((r) => r.data);
};
export const importLectureFiles = (id: number, paths: string[]) =>
    api.post<LectureFile[]>(`/diary/lectures/${id}/files/import`, { paths }, { timeout: 300_000 }).then((r) => r.data);
export const fetchLectureNasFiles = () => api.get<LectureNasFile[]>("/diary/lectures/nas-files").then((r) => r.data);
export const transcribeLectureFile = (fileId: number) =>
    api.post<LectureFile>(`/diary/lectures/files/${fileId}/transcribe`).then((r) => r.data);
export const deleteLectureFile = (fileId: number) => api.delete(`/diary/lectures/files/${fileId}`);
export const lectureFileUrl = (fileId: number) => `/api/diary/lectures/files/${fileId}`;

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
/** 메모 하나에 강연이 여럿이면 강연마다 요약을 만든다(같은 메모에서 나온 요약 목록을 돌려줌). */
export const summarizeNoteAsLecture = (noteId: number) =>
    api.post<Lecture[]>(`/diary/notes/${noteId}/lecture`, null, { timeout: 300_000 }).then((r) => r.data);
export const regenerateLecture = (id: number) =>
    api.post<Lecture>(`/diary/lectures/${id}/regenerate`, null, { timeout: 180_000 }).then((r) => r.data);
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

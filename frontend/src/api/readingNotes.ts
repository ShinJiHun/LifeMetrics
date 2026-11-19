import api from "@/lib/axios";

/** 올린 독서 기록 CSV 1개. 서버 ReadingNoteService.NoteFile 과 1:1. */
export interface NoteFile {
    filename: string; // 서버 저장명 "20261119-230000__원본.csv"
    originalName: string;
    size: number;
    uploadedAt: string;
    rows: number | null; // 머리글 제외 행 수(대략)
}

const BASE = "/human/reading-notes";

export async function fetchNoteFiles(): Promise<NoteFile[]> {
    const res = await api.get<NoteFile[]>(BASE);
    return res.data;
}

export async function uploadNoteFiles(files: File[]): Promise<NoteFile[]> {
    const form = new FormData();
    files.forEach((f) => form.append("files", f));
    const res = await api.post<NoteFile[]>(BASE, form, {
        headers: { "Content-Type": "multipart/form-data" },
    });
    return res.data;
}

export async function deleteNoteFile(filename: string): Promise<void> {
    await api.delete(`${BASE}/${encodeURIComponent(filename)}`);
}

export function noteFileUrl(filename: string): string {
    return `/api${BASE}/${encodeURIComponent(filename)}`;
}

// 인간 페르소나 — 독서 노트 기록 (관리자 전용).
// 독서 앱에서 내보낸 CSV 를 NAS(persona/reading/uploads)에 올려 보관만 한다.
// 독서 노트·후기 글은 Claude Code 에 요청하면 이 파일을 읽어 휴먼 블로그 '독서' 메뉴에 넣는다.
import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { deleteNoteFile, fetchNoteFiles, noteFileUrl, uploadNoteFiles, type NoteFile } from "@/api/readingNotes";

const ACCENT = "#7c3aed";
const INK = "#111827";
const MUTED = "#6b7280";
const LINE = "#e5e7eb";

function errorMessage(e: unknown): string {
    const anyErr = e as { response?: { status?: number; data?: { message?: string; error?: string } } };
    const status = anyErr.response?.status;
    if (status === 403) return "관리자 로그인이 필요합니다.";
    if (status === 413) return "파일이 너무 큽니다 (최대 50MB).";
    return anyErr.response?.data?.message || anyErr.response?.data?.error || "요청에 실패했습니다.";
}

function fmtSize(n: number): string {
    if (n < 1024) return `${n}B`;
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`;
    return `${(n / 1024 / 1024).toFixed(1)}MB`;
}

function fmtDate(iso: string): string {
    return new Date(iso).toLocaleString("ko-KR", {
        year: "2-digit", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
    });
}

export default function ReadingNotesPage() {
    const [files, setFiles] = useState<NoteFile[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [uploading, setUploading] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);

    const load = async () => {
        setLoading(true);
        try {
            setFiles(await fetchNoteFiles());
            setError(null);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    const onUpload = async (list: FileList | null) => {
        if (!list || list.length === 0) return;
        setUploading(true);
        setError(null);
        setNotice(null);
        try {
            const saved = await uploadNoteFiles([...list]);
            setNotice(`${saved.length}개 파일을 올렸어요.`);
            await load();
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setUploading(false);
            if (inputRef.current) inputRef.current.value = "";
        }
    };

    const onDelete = async (f: NoteFile) => {
        if (!window.confirm(`'${f.originalName}' 파일을 지울까요?`)) return;
        try {
            await deleteNoteFile(f.filename);
            await load();
        } catch (e) {
            setError(errorMessage(e));
        }
    };

    return (
        <div style={S.page}>
            <h1 style={S.h1}>📚 독서 노트 기록</h1>
            <p style={S.lead}>
                독서 앱에서 내보낸 <b>CSV</b>를 올려 두는 곳이에요. 독서 노트나 후기 글은
                Claude 에게 요청하면 이 파일을 바탕으로 <b>독서</b> 메뉴에 작성해 줘요.
            </p>

            {error && <div style={S.error}>{error}</div>}
            {notice && <div style={S.notice}>{notice}</div>}

            <section style={S.card}>
                <h2 style={S.h2}>CSV 올리기</h2>
                <input
                    ref={inputRef}
                    type="file"
                    multiple
                    accept=".csv,text/csv"
                    disabled={uploading}
                    onChange={(e) => onUpload(e.target.files)}
                />
                {uploading && <span style={{ marginLeft: 8, color: MUTED }}>올리는 중…</span>}
            </section>

            <section style={S.card}>
                <h2 style={S.h2}>올린 파일</h2>
                {loading ? (
                    <p style={S.hint}>불러오는 중…</p>
                ) : files.length === 0 ? (
                    <p style={S.hint}>아직 올린 파일이 없어요.</p>
                ) : (
                    <ul style={S.list}>
                        {files.map((f) => (
                            <li key={f.filename} style={S.row}>
                                <div style={S.rowMain}>
                                    <span style={{ fontWeight: 600, wordBreak: "break-all" }}>{f.originalName}</span>
                                    <span style={S.meta}>
                                        {f.rows != null && `${f.rows.toLocaleString("ko-KR")}행 · `}
                                        {fmtSize(f.size)} · {fmtDate(f.uploadedAt)}
                                    </span>
                                </div>
                                <div style={S.rowSide}>
                                    <a href={noteFileUrl(f.filename)} style={S.link}>
                                        내려받기
                                    </a>
                                    <button type="button" onClick={() => onDelete(f)} style={S.linkBtn}>
                                        삭제
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </section>
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 820, margin: "0 auto", padding: "24px 16px 48px", color: INK },
    h1: { fontSize: 22, margin: "0 0 8px" },
    lead: { color: MUTED, margin: "0 0 16px", lineHeight: 1.6 },
    h2: { fontSize: 16, margin: "0 0 10px" },
    card: { border: `1px solid ${LINE}`, borderRadius: 10, padding: 16, marginBottom: 16, background: "#fff" },
    hint: { color: MUTED, fontSize: 13, margin: "4px 0" },
    error: { background: "#fef2f2", color: "#b91c1c", padding: "10px 12px", borderRadius: 8, marginBottom: 16 },
    notice: { background: "#f5f3ff", color: ACCENT, padding: "10px 12px", borderRadius: 8, marginBottom: 16 },
    list: { listStyle: "none", margin: 0, padding: 0 },
    row: { borderTop: `1px solid ${LINE}`, padding: "10px 0", display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" },
    rowMain: { display: "flex", flexDirection: "column", gap: 2, flex: "1 1 260px" },
    rowSide: { display: "flex", gap: 12 },
    meta: { color: MUTED, fontSize: 12 },
    link: { color: ACCENT, fontSize: 14 },
    linkBtn: { background: "none", border: "none", color: "#b91c1c", fontSize: 14, cursor: "pointer", padding: 0 },
};

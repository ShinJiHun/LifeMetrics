// 직접 기록한 강연의 자료 파일 — 올리기(.md .txt .pdf .png .jpg .heic .m4a .mp3), NAS 강연 폴더에서 복사해 가져오기, 받아쓰기, 지우기.
import { useState } from "react";
import type { ChangeEvent } from "react";
import {
    deleteLectureFile,
    errorMessage,
    fetchLectureNasFiles,
    importLectureFiles,
    lectureFileUrl,
    transcribeLectureFile,
    uploadLectureFiles,
    type LectureFile,
    type LectureNasFile,
} from "@/api/diary";

const ACCEPT = ".md,.txt,.pdf,.png,.jpg,.jpeg,.heic,.m4a,.mp3";
const KIND_ICON: Record<LectureFile["kind"], string> = { text: "📝", pdf: "📄", image: "🖼", audio: "🎙" };

const size = (b: number) =>
    b >= 1024 * 1024 ? `${(b / 1024 / 1024).toFixed(1)}MB` : b >= 1024 ? `${Math.round(b / 1024)}KB` : `${b}B`;

function statusText(f: LectureFile) {
    switch (f.textStatus) {
        case "ready":
            return `요약에 씀 · ${f.textChars.toLocaleString()}자`;
        case "pending":
            return "받아쓰기 전";
        case "transcribing":
            return "받아쓰는 중…";
        case "failed":
            return `받아쓰기 실패${f.textNote ? `: ${f.textNote}` : ""}`;
        default:
            return f.textNote ?? "요약에 쓰지 않음";
    }
}

export default function LectureFiles({
    lectureId,
    files,
    busy,
    editable,
    run,
}: {
    lectureId: number;
    files: LectureFile[];
    busy: boolean;
    editable: boolean;
    run: (label: string, fn: () => Promise<unknown>) => void;
}) {
    const [nas, setNas] = useState<LectureNasFile[] | null>(null);
    const [picked, setPicked] = useState<Set<string>>(new Set());
    const [msg, setMsg] = useState<string | null>(null);

    const onPick = (e: ChangeEvent<HTMLInputElement>) => {
        const list = Array.from(e.target.files ?? []);
        e.target.value = "";
        if (!list.length) return;
        setMsg(null);
        run("files", async () => {
            const out = await uploadLectureFiles(lectureId, list);
            setMsg(`${out.length}개 올림`);
        });
    };

    const openNas = async () => {
        setMsg(null);
        try {
            setNas(await fetchLectureNasFiles());
            setPicked(new Set());
        } catch (e) {
            setMsg(errorMessage(e));
        }
    };

    const importPicked = () =>
        run("files", async () => {
            const out = await importLectureFiles(lectureId, [...picked]);
            setNas(null);
            setMsg(`${out.length}개 복사해 가져옴 (원본은 그대로)`);
        });

    return (
        <div className="lecture-files">
            <div className="lecture-files-head">
                <b>자료 {files.length}</b>
                {editable && (
                    <>
                        <label className={`diary-file ${busy ? "disabled" : ""}`}>
                            📎 파일 올리기
                            <input type="file" accept={ACCEPT} multiple hidden disabled={busy} onChange={onPick} />
                        </label>
                        <button onClick={nas ? () => setNas(null) : openNas} disabled={busy}>
                            {nas ? "NAS 닫기" : "🗄 NAS에서 가져오기"}
                        </button>
                    </>
                )}
            </div>
            {editable && (
                <div className="diary-muted small">
                    받아쓰기·메모(.md .txt)와 PDF 글이 요약 근거가 됩니다. 녹음(.m4a .mp3)은 STT 서버가 떠 있으면 자동으로 받아씁니다. 큰 녹음은 NAS 강연 폴더에 두고 가져오기로 넣으세요.
                </div>
            )}
            {msg && <div className="diary-drop-result">{msg}</div>}

            {nas && (
                <div className="lecture-nas">
                    {nas.length === 0 && <div className="diary-muted">NAS 강연 폴더에 가져올 파일이 없습니다.</div>}
                    {nas.map((n) => (
                        <label key={n.path} className="lecture-nas-row">
                            <input
                                type="checkbox"
                                checked={picked.has(n.path)}
                                onChange={(e) =>
                                    setPicked((p) => {
                                        const s = new Set(p);
                                        if (e.target.checked) s.add(n.path);
                                        else s.delete(n.path);
                                        return s;
                                    })
                                }
                            />
                            <span className="path">{n.path}</span>
                            <span className="diary-muted">
                                {size(n.sizeBytes)} · {n.modifiedAt.slice(0, 10)}
                            </span>
                        </label>
                    ))}
                    {nas.length > 0 && (
                        <button className="primary" disabled={busy || picked.size === 0} onClick={importPicked}>
                            {picked.size}개 복사해 가져오기
                        </button>
                    )}
                </div>
            )}

            {files.map((f) => (
                <div key={f.id} className={`lecture-file st-${f.textStatus}`}>
                    <span>{KIND_ICON[f.kind]}</span>
                    <a className="diary-link" href={lectureFileUrl(f.id)} target="_blank" rel="noreferrer">
                        {f.fileName}
                    </a>
                    <span className="diary-muted">{size(f.sizeBytes)}</span>
                    <span className="status">{statusText(f)}</span>
                    {editable && f.kind === "audio" && (f.textStatus === "pending" || f.textStatus === "failed") && (
                        <button className="diary-link" disabled={busy} onClick={() => run("files", () => transcribeLectureFile(f.id))}>
                            받아쓰기
                        </button>
                    )}
                    {editable && (
                        <button
                            className="diary-link danger"
                            disabled={busy || f.textStatus === "transcribing"}
                            onClick={() =>
                                window.confirm(`'${f.fileName}'을(를) 자료에서 뺄까요? (NAS 파일은 deleted 폴더로 옮겨 둡니다)`) &&
                                run("files", () => deleteLectureFile(f.id))
                            }
                        >
                            빼기
                        </button>
                    )}
                </div>
            ))}
        </div>
    );
}

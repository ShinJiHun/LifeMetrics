// 강연 정보 입력 폼 — [+ 강연 직접 기록]과 카드의 [정보 고치기]에서 같이 쓴다.
import { useState } from "react";
import type { LectureInfo } from "@/api/diary";

const kstToday = () => new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 10);

export const EMPTY_INFO: LectureInfo = {
    title: "",
    speaker: "",
    date: "",
    time: "",
    venue: "",
    host: "",
    series: "",
    books: [],
    memo: "",
};

export default function LectureInfoForm({
    initial,
    busy,
    submitLabel,
    showMemo = true,
    onSubmit,
    onCancel,
}: {
    initial: LectureInfo;
    busy: boolean;
    submitLabel: string;
    /** 일기 메모에서 나온 요약은 원본 메모가 있으니 '내 메모' 칸을 숨길 수 있다. */
    showMemo?: boolean;
    onSubmit: (info: LectureInfo) => void;
    onCancel: () => void;
}) {
    const [f, setF] = useState<LectureInfo>({ ...initial, date: initial.date || kstToday() });
    const [booksText, setBooksText] = useState(initial.books.join("\n"));
    const set = (k: keyof LectureInfo) => (e: { target: { value: string } }) => setF((p) => ({ ...p, [k]: e.target.value }));

    const submit = () => {
        if (!f.title.trim()) return;
        const books = booksText
            .split("\n")
            .map((b) => b.trim())
            .filter(Boolean);
        onSubmit({ ...f, books });
    };

    return (
        <div className="lecture-form">
            <label className="wide">
                강연명 *
                <input value={f.title} onChange={set("title")} placeholder="예: AI 시대, 인간다움이란" autoFocus />
            </label>
            <label>
                연사
                <input value={f.speaker} onChange={set("speaker")} placeholder="여럿이면 쉼표로" />
            </label>
            <label>
                날짜
                <input type="date" value={f.date} onChange={set("date")} />
            </label>
            <label>
                시각
                <input value={f.time} onChange={set("time")} placeholder="예: 14:00" />
            </label>
            <label>
                장소
                <input value={f.venue} onChange={set("venue")} />
            </label>
            <label>
                주최
                <input value={f.host} onChange={set("host")} />
            </label>
            <label>
                시리즈·회차
                <input value={f.series} onChange={set("series")} placeholder="예: GMC 1회" />
            </label>
            <label className="wide">
                관련 책 <small>(한 줄에 한 권)</small>
                <textarea value={booksText} onChange={(e) => setBooksText(e.target.value)} rows={2} />
            </label>
            {showMemo && (
                <label className="wide">
                    내 메모 <small>(들은 내용·내 생각 — 요약의 '내 생각'은 여기서만 씁니다)</small>
                    <textarea value={f.memo} onChange={set("memo")} rows={8} />
                </label>
            )}
            <div className="diary-form-row wide">
                <button onClick={onCancel}>취소</button>
                <button className="primary" onClick={submit} disabled={busy || !f.title.trim()}>
                    {submitLabel}
                </button>
            </div>
        </div>
    );
}

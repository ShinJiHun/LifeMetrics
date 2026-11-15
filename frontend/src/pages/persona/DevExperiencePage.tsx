// 개발자 경험 카드 관리 (관리자 전용).
// 프로젝트 중 있었던 일을 "상황 → 원인 → 결정 → 결과 → 배운 점"으로 남기면 개발자 페르소나 챗이 공개 카드를 근거로 답한다.
// 커밋 기록·메모를 붙여 넣으면 Claude 가 초안을 만들고, 검토·수정 후 저장한다.
import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import {
    deleteExperience,
    draftExperiences,
    emptyExperience,
    fetchExperiences,
    saveExperience,
} from "@/api/devExperience";
import type { DevExperience } from "@/api/devExperience";

const FIELDS: Array<{ key: keyof DevExperience; label: string; rows: number; hint: string }> = [
    { key: "situation", label: "상황·문제 *", rows: 3, hint: "무슨 일이 있었나" },
    { key: "cause", label: "원인 분석", rows: 3, hint: "어떻게 원인을 찾았나" },
    { key: "decision", label: "결정과 이유", rows: 3, hint: "고려한 대안과 고른 이유" },
    { key: "result", label: "결과", rows: 2, hint: "가능하면 숫자로" },
    { key: "lesson", label: "배운 점", rows: 2, hint: "다음에 다르게 할 것" },
];

const errorMessage = (e: unknown, fallback: string) => {
    const res = (e as { response?: { status?: number; data?: { message?: string } } }).response;
    if (res?.status === 401 || res?.status === 403) return "관리자 로그인이 필요합니다.";
    return res?.data?.message ?? fallback;
};

export default function DevExperiencePage() {
    const [items, setItems] = useState<DevExperience[]>([]);
    const [editing, setEditing] = useState<DevExperience | null>(null);
    const [drafts, setDrafts] = useState<DevExperience[]>([]);
    const [draftProject, setDraftProject] = useState("LifeMetrics");
    const [draftNotes, setDraftNotes] = useState("");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<string | null>(null);

    const load = () => fetchExperiences().then(setItems).catch((e) => setMessage(errorMessage(e, "목록을 불러오지 못했습니다.")));

    useEffect(() => {
        void load();
    }, []);

    const onSave = async () => {
        if (!editing) return;
        setBusy(true);
        setMessage(null);
        try {
            const saved = await saveExperience(editing);
            setDrafts((prev) => prev.filter((d) => d !== editing));
            setEditing(null);
            setMessage(`저장했습니다: ${saved.title}`);
            await load();
        } catch (e) {
            setMessage(errorMessage(e, "저장하지 못했습니다."));
        } finally {
            setBusy(false);
        }
    };

    const onDelete = async (e: DevExperience) => {
        if (!e.id || !window.confirm(`"${e.title}" 카드를 삭제할까요?`)) return;
        try {
            await deleteExperience(e.id);
            if (editing?.id === e.id) setEditing(null);
            await load();
        } catch (err) {
            setMessage(errorMessage(err, "삭제하지 못했습니다."));
        }
    };

    const onDraft = async () => {
        setBusy(true);
        setMessage(null);
        try {
            const result = await draftExperiences(draftProject.trim(), draftNotes);
            setDrafts(result);
            setMessage(result.length ? `초안 ${result.length}개를 만들었습니다. 열어서 검토한 뒤 저장하세요.` : "카드로 만들 만한 사건을 찾지 못했습니다.");
        } catch (e) {
            setMessage(errorMessage(e, "초안을 만들지 못했습니다."));
        } finally {
            setBusy(false);
        }
    };

    const set = (key: keyof DevExperience, value: string) =>
        setEditing((prev) => (prev ? { ...prev, [key]: value === "" && key !== "title" && key !== "situation" ? null : value } : prev));

    return (
        <div style={S.page}>
            <header style={S.header}>
                <h2 style={S.title}>🗂️ 개발 경험 카드</h2>
                <button type="button" style={S.primary} onClick={() => setEditing(emptyExperience())}>
                    새 카드
                </button>
            </header>
            <p style={S.muted}>
                공개(PUBLIC) 카드만 방문자용 개발자 페르소나 챗의 근거로 쓰입니다. 회사 내부 정보는 비공개로 두세요.
            </p>
            {message && <p style={S.message}>{message}</p>}

            {editing && (
                <section style={S.editor}>
                    <div style={S.row}>
                        <label style={S.field}>
                            <span style={S.label}>프로젝트 *</span>
                            <input style={S.input} value={editing.project} onChange={(e) => set("project", e.target.value)} />
                        </label>
                        <label style={S.fieldSmall}>
                            <span style={S.label}>날짜</span>
                            <input type="date" style={S.input} value={editing.happenedOn ?? ""} onChange={(e) => set("happenedOn", e.target.value)} />
                        </label>
                        <label style={S.fieldSmall}>
                            <span style={S.label}>공개 범위</span>
                            <select style={S.input} value={editing.visibility} onChange={(e) => set("visibility", e.target.value)}>
                                <option value="PRIVATE">비공개</option>
                                <option value="PUBLIC">공개</option>
                            </select>
                        </label>
                    </div>
                    <label style={S.fieldFull}>
                        <span style={S.label}>제목 *</span>
                        <input style={S.input} value={editing.title} onChange={(e) => set("title", e.target.value)} />
                    </label>
                    {FIELDS.map((f) => (
                        <label key={f.key} style={S.fieldFull}>
                            <span style={S.label}>{f.label}</span>
                            <textarea
                                style={S.textarea}
                                rows={f.rows}
                                placeholder={f.hint}
                                value={(editing[f.key] as string | null) ?? ""}
                                onChange={(e) => set(f.key, e.target.value)}
                            />
                        </label>
                    ))}
                    <div style={S.row}>
                        <label style={S.field}>
                            <span style={S.label}>태그 (쉼표 구분)</span>
                            <input style={S.input} value={editing.tags ?? ""} onChange={(e) => set("tags", e.target.value)} />
                        </label>
                        <label style={S.field}>
                            <span style={S.label}>근거 (커밋·링크)</span>
                            <input style={S.input} value={editing.sourceRef ?? ""} onChange={(e) => set("sourceRef", e.target.value)} />
                        </label>
                    </div>
                    <div style={S.actions}>
                        <button type="button" style={S.primary} onClick={() => void onSave()} disabled={busy}>
                            저장
                        </button>
                        <button type="button" style={S.ghost} onClick={() => setEditing(null)} disabled={busy}>
                            취소
                        </button>
                    </div>
                </section>
            )}

            {drafts.length > 0 && (
                <section>
                    <h3 style={S.subTitle}>초안 (저장 전)</h3>
                    {drafts.map((d, i) => (
                        <Card key={`draft-${i}`} e={d} onOpen={() => setEditing(d)} draft />
                    ))}
                </section>
            )}

            <section>
                <h3 style={S.subTitle}>저장된 카드 {items.length}개</h3>
                {items.map((e) => (
                    <Card key={e.id} e={e} onOpen={() => setEditing({ ...e })} onDelete={() => void onDelete(e)} />
                ))}
            </section>

            <section style={S.draftBox}>
                <h3 style={S.subTitle}>커밋 기록·메모로 초안 만들기</h3>
                <p style={S.muted}>
                    <code>git log --format="%h %ad %s%n%b" --date=short</code> 결과나 작업 메모를 붙여 넣으면 Claude 가 면접에서 이야기할
                    만한 사건만 골라 카드 초안을 만듭니다. 입력에 없는 내용은 지어내지 않도록 했고, 초안은 모두 비공개로 시작합니다.
                </p>
                <input style={{ ...S.input, marginBottom: 8 }} value={draftProject} onChange={(e) => setDraftProject(e.target.value)} placeholder="프로젝트명" />
                <textarea style={S.textarea} rows={8} value={draftNotes} onChange={(e) => setDraftNotes(e.target.value)} placeholder="커밋 기록 또는 메모" />
                <button type="button" style={{ ...S.primary, marginTop: 8 }} onClick={() => void onDraft()} disabled={busy || !draftNotes.trim() || !draftProject.trim()}>
                    {busy ? "만드는 중…" : "초안 만들기"}
                </button>
            </section>
        </div>
    );
}

function Card({ e, onOpen, onDelete, draft }: { e: DevExperience; onOpen: () => void; onDelete?: () => void; draft?: boolean }) {
    return (
        <div style={S.card}>
            <div style={S.cardHead}>
                <button type="button" style={S.cardTitle} onClick={onOpen}>
                    {e.title}
                </button>
                <span style={e.visibility === "PUBLIC" ? S.public : S.private}>{e.visibility === "PUBLIC" ? "공개" : "비공개"}</span>
                {draft && <span style={S.draftBadge}>초안</span>}
                {onDelete && (
                    <button type="button" style={S.delete} onClick={onDelete}>
                        삭제
                    </button>
                )}
            </div>
            <div style={S.meta}>
                {e.project}
                {e.happenedOn ? ` · ${e.happenedOn}` : ""}
                {e.tags ? ` · ${e.tags}` : ""}
            </div>
            <div style={S.summary}>{e.situation}</div>
        </div>
    );
}

const input: CSSProperties = { padding: "8px 10px", borderRadius: 8, border: "1px solid #d6d9e4", fontSize: 14, fontFamily: "inherit", width: "100%", boxSizing: "border-box" };
const badge: CSSProperties = { fontSize: 11, padding: "2px 8px", borderRadius: 999, fontWeight: 700, whiteSpace: "nowrap" };
const button: CSSProperties = { borderRadius: 8, padding: "8px 16px", fontWeight: 700, cursor: "pointer", fontSize: 14 };

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 900, margin: "0 auto", padding: 16, boxSizing: "border-box" },
    header: { display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 },
    title: { margin: 0, fontSize: 20 },
    subTitle: { fontSize: 16, margin: "20px 0 8px" },
    muted: { color: "#6b7186", fontSize: 13, lineHeight: 1.6 },
    message: { padding: "8px 12px", background: "#eff6ff", borderRadius: 8, fontSize: 14 },
    editor: { border: "1px solid #c7d2fe", borderRadius: 12, padding: 16, background: "#fafbff", marginTop: 12 },
    row: { display: "flex", gap: 12, flexWrap: "wrap" },
    field: { flex: "1 1 220px", display: "flex", flexDirection: "column", gap: 4, marginBottom: 10 },
    fieldSmall: { flex: "0 1 160px", display: "flex", flexDirection: "column", gap: 4, marginBottom: 10 },
    fieldFull: { display: "flex", flexDirection: "column", gap: 4, marginBottom: 10 },
    label: { fontSize: 13, fontWeight: 600, color: "#374151" },
    input,
    textarea: { ...input, resize: "vertical", lineHeight: 1.5 },
    actions: { display: "flex", gap: 8 },
    primary: { ...button, border: "none", background: "#2563eb", color: "#fff" },
    ghost: { ...button, border: "1px solid #d6d9e4", background: "#fff", color: "#374151" },
    card: { border: "1px solid #e5e7ef", borderRadius: 10, padding: "10px 14px", marginBottom: 8, background: "#fff" },
    cardHead: { display: "flex", alignItems: "center", gap: 8 },
    cardTitle: { flex: 1, textAlign: "left", border: "none", background: "transparent", fontSize: 15, fontWeight: 700, cursor: "pointer", padding: 0, color: "#1e2235" },
    public: { ...badge, background: "#dcfce7", color: "#166534" },
    private: { ...badge, background: "#f1f5f9", color: "#475569" },
    draftBadge: { ...badge, background: "#fef3c7", color: "#92400e" },
    delete: { border: "none", background: "transparent", color: "#b91c1c", fontSize: 12, cursor: "pointer" },
    meta: { fontSize: 12, color: "#8a90a6", margin: "4px 0" },
    summary: { fontSize: 14, color: "#374151", lineHeight: 1.5 },
    draftBox: { marginTop: 24, padding: 16, border: "1px dashed #c7d2fe", borderRadius: 12 },
};

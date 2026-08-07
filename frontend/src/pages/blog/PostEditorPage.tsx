import { useMemo, useState } from "react";
import type { CSSProperties, ReactNode } from "react";
import { Link, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { Visibility } from "@/types/content";
import RichText from "@/components/common/RichText";
import RichTextEditor from "@/components/common/RichTextEditor";
import { PERSONA_META, blogPersonaFromPath } from "@/lib/persona";
import {
    addPost,
    categoriesOf,
    findPost,
    subMenusOf,
    updatePost,
    useContent,
} from "@/lib/contentStore";

export default function PostEditorPage() {
    const { pathname } = useLocation();
    const persona = blogPersonaFromPath(pathname);
    const accent = PERSONA_META[persona].accent;
    const { postId } = useParams();
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const data = useContent();

    const editing = postId ? findPost(data, postId) : undefined;
    const isEdit = Boolean(postId);

    // 페르소나의 (대메뉴 > 소메뉴) 옵션 목록
    const subOptions = useMemo(() => {
        const opts: { id: string; label: string }[] = [];
        for (const cat of categoriesOf(data, persona)) {
            for (const sub of subMenusOf(data, cat.id)) {
                opts.push({ id: sub.id, label: `${cat.name} / ${sub.name}` });
            }
        }
        return opts;
    }, [data, persona]);

    const initialSub = editing?.subId ?? searchParams.get("sub") ?? subOptions[0]?.id ?? "";
    const [subId, setSubId] = useState(initialSub);
    const [title, setTitle] = useState(editing?.title ?? "");
    const [body, setBody] = useState(editing?.body ?? "");
    const [visibility, setVisibility] = useState<Visibility>(editing?.visibility ?? "public");
    const [mode, setMode] = useState<"write" | "html" | "preview">("write");

    if (subOptions.length === 0) {
        return (
            <div style={S.page}>
                <p style={S.muted}>먼저 메뉴(대메뉴·소메뉴)를 만들어야 글을 쓸 수 있어요.</p>
                {persona === "human" && (
                    <Link to={`/${persona}/manage`} style={{ color: accent, fontWeight: 600 }}>
                        메뉴 관리로 가기 →
                    </Link>
                )}
            </div>
        );
    }

    const [saving, setSaving] = useState(false);

    const save = async () => {
        if (!subId || saving) return;
        setSaving(true);
        try {
            if (isEdit && editing) {
                await updatePost(editing.id, { subId, title, body, visibility });
                navigate(`/${persona}/post/${editing.id}`);
            } else {
                const created = await addPost({ subId, title, body, visibility });
                navigate(`/${persona}/post/${created.id}`);
            }
        } catch (e) {
            console.error(e);
            alert("저장에 실패했습니다. 백엔드 연결을 확인해 주세요.");
            setSaving(false);
        }
    };

    return (
        <div style={S.page}>
            <h1 style={S.title}>{isEdit ? "글 수정" : "새 글"}</h1>

            <label style={S.label}>메뉴</label>
            <select value={subId} onChange={(e) => setSubId(e.target.value)} style={S.input}>
                {subOptions.map((o) => (
                    <option key={o.id} value={o.id}>
                        {o.label}
                    </option>
                ))}
            </select>

            <label style={S.label}>제목</label>
            <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="예: 2.1 변수 / 1주차 회고"
                style={S.input}
            />

            <div style={S.contentHead}>
                <label style={{ ...S.label, margin: 0 }}>내용</label>
                <div style={S.tabs}>
                    <TabBtn active={mode === "write"} accent={accent} onClick={() => setMode("write")}>
                        ✏️ 쓰기
                    </TabBtn>
                    <TabBtn active={mode === "html"} accent={accent} onClick={() => setMode("html")}>
                        {"</>"} HTML
                    </TabBtn>
                    <TabBtn active={mode === "preview"} accent={accent} onClick={() => setMode("preview")}>
                        👁 미리보기
                    </TabBtn>
                </div>
            </div>
            {mode === "preview" ? (
                <div style={S.previewBox}>
                    {body.trim() ? <RichText>{body}</RichText> : <span style={S.muted}>미리볼 내용이 없습니다.</span>}
                </div>
            ) : mode === "html" ? (
                <textarea
                    value={body}
                    onChange={(e) => setBody(e.target.value)}
                    placeholder="HTML을 직접 입력하거나 붙여넣으세요"
                    style={S.htmlBox}
                />
            ) : (
                <RichTextEditor
                    value={body}
                    onChange={setBody}
                    accent={accent}
                    placeholder="내용을 입력하세요"
                />
            )}

            <label style={S.label}>공개 범위</label>
            <div style={{ display: "flex", gap: 10, marginBottom: 28 }}>
                <ToggleBtn
                    active={visibility === "public"}
                    accent={accent}
                    onClick={() => setVisibility("public")}
                >
                    🌐 공개
                </ToggleBtn>
                <ToggleBtn
                    active={visibility === "private"}
                    accent={accent}
                    onClick={() => setVisibility("private")}
                >
                    🔒 비공개
                </ToggleBtn>
            </div>

            <div style={{ display: "flex", gap: 10 }}>
                <button
                    type="button"
                    onClick={save}
                    disabled={saving}
                    style={{ ...S.saveBtn, background: accent, opacity: saving ? 0.6 : 1 }}
                >
                    {saving ? "저장 중…" : "저장"}
                </button>
                <button type="button" onClick={() => navigate(-1)} style={S.cancelBtn}>
                    취소
                </button>
            </div>
        </div>
    );
}

function ToggleBtn({
    active,
    accent,
    onClick,
    children,
}: {
    active: boolean;
    accent: string;
    onClick: () => void;
    children: ReactNode;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            style={{
                flex: 1,
                padding: "12px 0",
                borderRadius: 12,
                border: `2px solid ${active ? accent : "#e5e7eb"}`,
                background: active ? accent : "#fff",
                color: active ? "#fff" : "#6b7280",
                fontWeight: 700,
                fontSize: 15,
                cursor: "pointer",
            }}
        >
            {children}
        </button>
    );
}

function TabBtn({
    active,
    accent,
    onClick,
    children,
}: {
    active: boolean;
    accent: string;
    onClick: () => void;
    children: ReactNode;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            style={{
                padding: "6px 14px",
                borderRadius: 999,
                border: "none",
                background: active ? accent : "transparent",
                color: active ? "#fff" : "#6b7280",
                fontWeight: 700,
                fontSize: 13,
                cursor: "pointer",
            }}
        >
            {children}
        </button>
    );
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 720, margin: "0 auto", padding: "40px 24px 80px" },
    title: { margin: "0 0 28px", fontSize: 28, fontWeight: 800, color: "#1b2236" },
    label: { display: "block", fontSize: 13, fontWeight: 700, color: "#6b7280", margin: "0 0 8px" },
    input: {
        width: "100%", boxSizing: "border-box", padding: "12px 14px", marginBottom: 22,
        borderRadius: 10, border: "1px solid #d8dbe6", fontSize: 15, fontFamily: "inherit",
        background: "#fff", color: "#1b2236",
    },
    saveBtn: {
        color: "#fff", border: "none", fontSize: 15, fontWeight: 700,
        padding: "12px 28px", borderRadius: 999, cursor: "pointer",
    },
    cancelBtn: {
        background: "#fff", color: "#6b7280", border: "1px solid #e5e7eb",
        fontSize: 15, fontWeight: 700, padding: "12px 24px", borderRadius: 999, cursor: "pointer",
    },
    muted: { color: "#9aa0b2", fontSize: 15, marginBottom: 12 },
    contentHead: { display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 8 },
    tabs: { display: "flex", gap: 4, background: "#f1f3f9", borderRadius: 999, padding: 3 },
    previewBox: {
        width: "100%", boxSizing: "border-box", padding: "16px 18px", marginBottom: 22,
        borderRadius: 10, border: "1px solid #d8dbe6", background: "#fff", minHeight: 320,
    },
    htmlBox: {
        width: "100%", boxSizing: "border-box", padding: "14px 16px", marginBottom: 22,
        borderRadius: 10, border: "1px solid #d8dbe6", background: "#0f172a", color: "#e2e8f0",
        minHeight: 320, fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
        fontSize: 13, lineHeight: 1.6, resize: "vertical",
    },
};

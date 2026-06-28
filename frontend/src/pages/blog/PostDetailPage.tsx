import type { CSSProperties } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { PERSONA_META, blogPersonaFromPath } from "@/lib/persona";
import { deletePost, findCategory, findPost, findSubMenu, useContent } from "@/lib/contentStore";

export default function PostDetailPage() {
    const { pathname } = useLocation();
    const persona = blogPersonaFromPath(pathname);
    const accent = PERSONA_META[persona].accent;
    const { postId = "" } = useParams();
    const navigate = useNavigate();
    const data = useContent();

    const post = findPost(data, postId);
    const sub = post ? findSubMenu(data, post.subId) : undefined;
    const category = sub ? findCategory(data, sub.categoryId) : undefined;

    if (!post) {
        return (
            <div style={S.page}>
                <p style={S.muted}>존재하지 않는 글입니다.</p>
                <Link to={`/${persona}`} style={{ color: accent }}>
                    ← 홈으로
                </Link>
            </div>
        );
    }

    const onDelete = async () => {
        if (!confirm("이 글을 삭제할까요?")) return;
        const backSub = post.subId;
        try {
            await deletePost(post.id);
            navigate(`/${persona}/sub/${backSub}`);
        } catch (e) {
            console.error(e);
            alert("삭제에 실패했습니다.");
        }
    };

    return (
        <div style={S.page}>
            <div style={S.crumb}>
                {sub && (
                    <Link to={`/${persona}/sub/${sub.id}`} style={{ color: "#9aa0b2", textDecoration: "none" }}>
                        ← {category?.name} / {sub.name}
                    </Link>
                )}
            </div>

            <div style={S.metaRow}>
                <span>{post.createdAt.slice(0, 10)}</span>
                {post.visibility === "private" ? (
                    <span style={S.lock}>🔒 비공개</span>
                ) : (
                    <span style={{ ...S.badge, color: accent, background: tint(accent) }}>공개</span>
                )}
            </div>

            <h1 style={S.title}>{post.title || "(제목 없음)"}</h1>

            <div style={S.body}>{post.body || <span style={S.muted}>(내용 없음)</span>}</div>

            <div style={S.actions}>
                <Link to={`/${persona}/post/${post.id}/edit`} style={{ ...S.btn, background: accent }}>
                    ✏️ 수정
                </Link>
                <button type="button" onClick={onDelete} style={S.delBtn}>
                    🗑️ 삭제
                </button>
            </div>
        </div>
    );
}

function tint(accent: string): string {
    return accent === "#16a34a" ? "#eaf7ee" : "#eef0fe";
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 720, margin: "0 auto", padding: "40px 24px 80px" },
    crumb: { fontSize: 13, marginBottom: 18 },
    metaRow: { display: "flex", alignItems: "center", gap: 10, fontSize: 13, color: "#9aa0b2" },
    lock: { color: "#b45309", fontWeight: 600 },
    badge: { fontSize: 12, fontWeight: 700, borderRadius: 999, padding: "3px 10px" },
    title: { margin: "12px 0 24px", fontSize: 32, fontWeight: 800, letterSpacing: "-0.02em", color: "#1b2236", lineHeight: 1.3 },
    body: { fontSize: 16, lineHeight: 1.85, color: "#2f3545", whiteSpace: "pre-wrap" },
    actions: { display: "flex", gap: 10, marginTop: 40 },
    btn: { color: "#fff", textDecoration: "none", fontSize: 14, fontWeight: 700, padding: "10px 18px", borderRadius: 999 },
    delBtn: {
        background: "#fff", color: "#dc2626", border: "1px solid #f3c9c9",
        fontSize: 14, fontWeight: 700, padding: "10px 18px", borderRadius: 999, cursor: "pointer",
    },
    muted: { color: "#9aa0b2", fontSize: 15 },
};

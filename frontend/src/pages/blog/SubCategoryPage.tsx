import type { CSSProperties } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { PERSONA_META, blogPersonaFromPath } from "@/lib/persona";
import { findCategory, findSubMenu, postsOf, useContent } from "@/lib/contentStore";

export default function SubCategoryPage() {
    const { pathname } = useLocation();
    const persona = blogPersonaFromPath(pathname);
    const accent = PERSONA_META[persona].accent;
    const { subId = "" } = useParams();
    const data = useContent();

    const sub = findSubMenu(data, subId);
    const category = sub ? findCategory(data, sub.categoryId) : undefined;
    const posts = postsOf(data, subId);

    if (!sub) {
        return (
            <div style={S.page}>
                <p style={S.muted}>존재하지 않는 메뉴입니다.</p>
                <Link to={`/${persona}`} style={{ color: accent }}>
                    ← 홈으로
                </Link>
            </div>
        );
    }

    return (
        <div style={S.page}>
            <div style={S.crumb}>
                {category?.name} <span style={{ color: "#c7cbd9" }}>/</span> {sub.name}
            </div>
            <header style={S.head}>
                <h1 style={S.title}>{sub.name}</h1>
                <Link
                    to={`/${persona}/post/new?sub=${subId}`}
                    style={{ ...S.btn, background: accent }}
                >
                    + 새 글
                </Link>
            </header>

            {posts.length === 0 ? (
                <p style={S.muted}>아직 글이 없습니다. 첫 글을 작성해 보세요.</p>
            ) : (
                <section style={S.list}>
                    {posts.map((p) => (
                        <Link key={p.id} to={`/${persona}/post/${p.id}`} style={S.card}>
                            <div style={S.cardMeta}>
                                <span>{p.createdAt.slice(0, 10)}</span>
                                {p.visibility === "private" && (
                                    <span style={S.lock}>🔒 비공개</span>
                                )}
                            </div>
                            <h2 style={S.cardTitle}>{p.title || "(제목 없음)"}</h2>
                            <p style={S.cardSummary}>{summarize(p.body)}</p>
                        </Link>
                    ))}
                </section>
            )}
        </div>
    );
}

function summarize(body: string): string {
    const text = body.replace(/\s+/g, " ").trim();
    return text.length > 120 ? text.slice(0, 120) + "…" : text;
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 760, margin: "0 auto", padding: "40px 24px 64px" },
    crumb: { fontSize: 13, color: "#9aa0b2", marginBottom: 8 },
    head: { display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 28 },
    title: { margin: 0, fontSize: 30, fontWeight: 800, letterSpacing: "-0.02em", color: "#1b2236" },
    btn: {
        color: "#fff", textDecoration: "none", fontSize: 14, fontWeight: 700,
        padding: "10px 16px", borderRadius: 999, whiteSpace: "nowrap",
    },
    list: { display: "flex", flexDirection: "column", gap: 16 },
    card: {
        display: "block", background: "#fff", borderRadius: 16, border: "1px solid #eceef5",
        padding: "20px 24px", textDecoration: "none",
        boxShadow: "0 8px 24px rgba(30,41,90,0.05)",
    },
    cardMeta: { display: "flex", alignItems: "center", gap: 10, fontSize: 13, color: "#9aa0b2" },
    lock: { color: "#b45309", fontWeight: 600 },
    cardTitle: { margin: "8px 0 0", fontSize: 19, fontWeight: 700, color: "#1b2236" },
    cardSummary: { margin: "10px 0 0", fontSize: 14, lineHeight: 1.7, color: "#5a6072" },
    muted: { color: "#9aa0b2", fontSize: 15 },
};

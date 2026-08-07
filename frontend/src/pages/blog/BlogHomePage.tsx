import AdminOnly from "@/components/common/AdminOnly";
import type { CSSProperties } from "react";
import { Link, useLocation } from "react-router-dom";
import { PERSONA_META, blogPersonaFromPath } from "@/lib/persona";
import { categoriesOf, postsOf, subMenusOf, useContent } from "@/lib/contentStore";

export default function BlogHomePage() {
    const { pathname } = useLocation();
    const persona = blogPersonaFromPath(pathname);
    const accent = PERSONA_META[persona].accent;
    const data = useContent();
    const categories = categoriesOf(data, persona);

    const heading =
        persona === "developer"
            ? { kicker: "DEVELOPER", title: "개발자 신지훈", sub: "코드, 프로젝트, 그리고 만들면서 배운 것들을 기록합니다." }
            : { kicker: "HUMAN", title: "인간 신지훈", sub: "일상에서 마주친 생각과 감정, 그리고 사람에 대한 기록." };

    return (
        <div style={S.page}>
            <header style={S.hero}>
                <div style={{ ...S.kicker, color: accent }}>{heading.kicker}</div>
                <h1 style={S.title}>{heading.title}</h1>
                <p style={S.subtitle}>{heading.sub}</p>
                {persona === "human" && (
                    <AdminOnly>
                        <div style={{ marginTop: 20, display: "flex", gap: 10 }}>
                            <Link to={`/${persona}/manage`} style={{ ...S.btn, background: accent }}>
                                ✏️ 메뉴·글 관리
                            </Link>
                        </div>
                    </AdminOnly>
                )}
            </header>

            {categories.length === 0 ? (
                <div style={S.empty}>
                    아직 메뉴가 없습니다.{" "}
                    {persona === "human" && (
                        <AdminOnly>
                            <Link to={`/${persona}/manage`} style={{ color: accent, fontWeight: 600 }}>
                                메뉴 만들기 →
                            </Link>
                        </AdminOnly>
                    )}
                </div>
            ) : (
                categories.map((cat) => {
                    const subs = subMenusOf(data, cat.id);
                    return (
                        <section key={cat.id} style={{ marginBottom: 36 }}>
                            <h2 style={S.catTitle}>📂 {cat.name}</h2>
                            {subs.length === 0 ? (
                                <p style={S.muted}>소메뉴가 없습니다.</p>
                            ) : (
                                <div style={S.subGrid}>
                                    {subs.map((s) => {
                                        const count = postsOf(data, s.id).length;
                                        return (
                                            <Link
                                                key={s.id}
                                                to={`/${persona}/sub/${s.id}`}
                                                style={S.subCard}
                                            >
                                                <span style={{ fontWeight: 700, color: "#1b2236" }}>
                                                    {s.name}
                                                </span>
                                                <span style={{ ...S.count, color: accent }}>
                                                    글 {count}개
                                                </span>
                                            </Link>
                                        );
                                    })}
                                </div>
                            )}
                        </section>
                    );
                })
            )}
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 760, margin: "0 auto", padding: "40px 24px 64px" },
    hero: { marginBottom: 40 },
    kicker: { fontSize: 13, fontWeight: 700, letterSpacing: "0.14em" },
    title: { margin: "10px 0 0", fontSize: 36, fontWeight: 800, letterSpacing: "-0.02em", color: "#1b2236" },
    subtitle: { margin: "12px 0 0", fontSize: 16, color: "#8a90a3", lineHeight: 1.6 },
    btn: {
        color: "#fff", textDecoration: "none", fontSize: 14, fontWeight: 700,
        padding: "10px 18px", borderRadius: 999,
    },
    catTitle: { fontSize: 20, fontWeight: 800, color: "#1b2236", margin: "0 0 14px" },
    subGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: 12 },
    subCard: {
        display: "flex", justifyContent: "space-between", alignItems: "center",
        background: "#fff", border: "1px solid #eceef5", borderRadius: 12,
        padding: "16px 18px", textDecoration: "none",
        boxShadow: "0 4px 14px rgba(30,41,90,0.04)",
    },
    count: { fontSize: 13, fontWeight: 600 },
    muted: { color: "#9aa0b2", fontSize: 14 },
    empty: { color: "#8a90a3", fontSize: 15, padding: "32px 0" },
};

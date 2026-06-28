import { useState } from "react";
import { NavLink } from "react-router-dom";
import type { Persona } from "@/types/content";
import { categoriesOf, subMenusOf, useContent } from "@/lib/contentStore";

const linkStyle = (accent: string) => ({ isActive }: { isActive: boolean }) => ({
    padding: "8px 14px 8px 28px",
    textDecoration: "none",
    color: isActive ? accent : "#555",
    fontWeight: isActive ? 700 : 400,
    fontSize: 14,
    display: "block",
});

export default function PersonaContentMenu({
    persona,
    accent,
}: {
    persona: Persona;
    accent: string;
}) {
    const data = useContent();
    const categories = categoriesOf(data, persona);
    const base = `/${persona}`;

    return (
        <>
            {/* 상단 관리/도구 링크 */}
            <nav style={{ display: "flex", flexDirection: "column", gap: 2, marginBottom: 18 }}>
                <NavLink to={base} end style={linkRoot(accent)}>
                    🏠 홈
                </NavLink>
                <NavLink to={`${base}/manage`} style={linkRoot(accent)}>
                    ✏️ 메뉴·글 관리
                </NavLink>
                {persona === "developer" && (
                    <NavLink to="/persona/developer" style={linkRoot(accent)}>
                        💬 페르소나 챗
                    </NavLink>
                )}
            </nav>

            {categories.length === 0 ? (
                <div style={{ fontSize: 13, color: "#9aa0b2", padding: "8px 14px", lineHeight: 1.7 }}>
                    아직 메뉴가 없어요.
                    <br />
                    <NavLink to={`${base}/manage`} style={{ color: accent }}>
                        메뉴 만들기 →
                    </NavLink>
                </div>
            ) : (
                categories.map((cat) => (
                    <CategoryBlock
                        key={cat.id}
                        categoryId={cat.id}
                        name={cat.name}
                        accent={accent}
                        base={base}
                        data={data}
                    />
                ))
            )}
        </>
    );
}

function CategoryBlock({
    categoryId,
    name,
    accent,
    base,
    data,
}: {
    categoryId: string;
    name: string;
    accent: string;
    base: string;
    data: ReturnType<typeof useContent>;
}) {
    const [open, setOpen] = useState(true);
    const subs = subMenusOf(data, categoryId);

    return (
        <div style={{ marginBottom: 10 }}>
            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 6,
                    width: "100%",
                    background: "none",
                    border: "none",
                    cursor: "pointer",
                    padding: "8px 6px",
                    font: "inherit",
                    fontWeight: 700,
                    color: "#1b2236",
                    textAlign: "left",
                }}
            >
                <span style={{ fontSize: 11, color: "#9aa0b2" }}>{open ? "▾" : "▸"}</span>
                <span>📂 {name}</span>
            </button>
            {open && (
                <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    {subs.length === 0 ? (
                        <span style={{ padding: "4px 14px 4px 28px", fontSize: 13, color: "#bcc1cf" }}>
                            (소메뉴 없음)
                        </span>
                    ) : (
                        subs.map((s) => (
                            <NavLink key={s.id} to={`${base}/sub/${s.id}`} style={linkStyle(accent)}>
                                {s.name}
                            </NavLink>
                        ))
                    )}
                </nav>
            )}
        </div>
    );
}

const linkRoot = (accent: string) => ({ isActive }: { isActive: boolean }) => ({
    padding: "8px 14px",
    textDecoration: "none",
    color: isActive ? accent : "#111",
    fontWeight: isActive ? 700 : 500,
    fontSize: 14,
});

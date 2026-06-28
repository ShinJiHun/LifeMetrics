import { useState } from "react";
import type { CSSProperties } from "react";
import { Link, useLocation } from "react-router-dom";
import { PERSONA_META, blogPersonaFromPath } from "@/lib/persona";
import {
    addCategory,
    addSubMenu,
    categoriesOf,
    deleteCategory,
    deleteSubMenu,
    postsOf,
    renameCategory,
    renameSubMenu,
    subMenusOf,
    useContent,
} from "@/lib/contentStore";

export default function MenuManagePage() {
    const { pathname } = useLocation();
    const persona = blogPersonaFromPath(pathname);
    const accent = PERSONA_META[persona].accent;
    const data = useContent();
    const categories = categoriesOf(data, persona);

    const [newCat, setNewCat] = useState("");

    const submitCat = () => {
        const name = newCat.trim();
        if (!name) return;
        addCategory(persona, name);
        setNewCat("");
    };

    return (
        <div style={S.page}>
            <header style={{ marginBottom: 28 }}>
                <div style={{ ...S.kicker, color: accent }}>MENU MANAGER</div>
                <h1 style={S.title}>메뉴·글 관리</h1>
                <p style={S.subtitle}>
                    큰 메뉴(대분류) 아래 작은 메뉴(소분류)를 만들고, 소분류에서 글을 작성합니다.
                </p>
            </header>

            {/* 대메뉴 추가 */}
            <section style={S.addBar}>
                <input
                    value={newCat}
                    onChange={(e) => setNewCat(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && submitCat()}
                    placeholder="큰 메뉴 이름 (예: 자바의 정석 / 회사 일기)"
                    style={S.input}
                />
                <button type="button" onClick={submitCat} style={{ ...S.addBtn, background: accent }}>
                    + 큰 메뉴 추가
                </button>
            </section>

            {categories.length === 0 ? (
                <p style={S.muted}>아직 큰 메뉴가 없습니다. 위에서 추가해 보세요.</p>
            ) : (
                categories.map((cat) => (
                    <CategoryCard
                        key={cat.id}
                        categoryId={cat.id}
                        name={cat.name}
                        accent={accent}
                        persona={persona}
                        data={data}
                    />
                ))
            )}
        </div>
    );
}

function CategoryCard({
    categoryId,
    name,
    accent,
    persona,
    data,
}: {
    categoryId: string;
    name: string;
    accent: string;
    persona: string;
    data: ReturnType<typeof useContent>;
}) {
    const subs = subMenusOf(data, categoryId);
    const [editing, setEditing] = useState(false);
    const [catName, setCatName] = useState(name);
    const [newSub, setNewSub] = useState("");

    const submitSub = () => {
        const n = newSub.trim();
        if (!n) return;
        addSubMenu(categoryId, n);
        setNewSub("");
    };

    const saveName = () => {
        const n = catName.trim();
        if (n) renameCategory(categoryId, n);
        else setCatName(name);
        setEditing(false);
    };

    return (
        <section style={S.card}>
            <div style={S.cardHead}>
                {editing ? (
                    <input
                        value={catName}
                        autoFocus
                        onChange={(e) => setCatName(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && saveName()}
                        onBlur={saveName}
                        style={{ ...S.input, marginBottom: 0, maxWidth: 280 }}
                    />
                ) : (
                    <h2 style={S.catName}>📂 {name}</h2>
                )}
                <div style={{ display: "flex", gap: 8 }}>
                    <button type="button" onClick={() => setEditing((v) => !v)} style={S.iconBtn}>
                        ✏️ 이름
                    </button>
                    <button
                        type="button"
                        onClick={() => {
                            if (confirm(`"${name}" 큰 메뉴와 그 안의 모든 소메뉴·글을 삭제할까요?`))
                                deleteCategory(categoryId);
                        }}
                        style={S.delBtn}
                    >
                        🗑️ 삭제
                    </button>
                </div>
            </div>

            {/* 소메뉴 목록 */}
            <div style={{ marginTop: 14, display: "flex", flexDirection: "column", gap: 8 }}>
                {subs.length === 0 ? (
                    <p style={{ ...S.muted, margin: "4px 0" }}>소메뉴가 없습니다.</p>
                ) : (
                    subs.map((s) => (
                        <SubRow
                            key={s.id}
                            subId={s.id}
                            name={s.name}
                            count={postsOf(data, s.id).length}
                            accent={accent}
                            persona={persona}
                        />
                    ))
                )}
            </div>

            {/* 소메뉴 추가 */}
            <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
                <input
                    value={newSub}
                    onChange={(e) => setNewSub(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && submitSub()}
                    placeholder="작은 메뉴 이름 (예: 1단원 / 26년)"
                    style={{ ...S.input, marginBottom: 0 }}
                />
                <button type="button" onClick={submitSub} style={S.subAddBtn}>
                    + 소메뉴
                </button>
            </div>
        </section>
    );
}

function SubRow({
    subId,
    name,
    count,
    accent,
    persona,
}: {
    subId: string;
    name: string;
    count: number;
    accent: string;
    persona: string;
}) {
    const [editing, setEditing] = useState(false);
    const [val, setVal] = useState(name);

    const save = () => {
        const n = val.trim();
        if (n) renameSubMenu(subId, n);
        else setVal(name);
        setEditing(false);
    };

    return (
        <div style={S.subRow}>
            {editing ? (
                <input
                    value={val}
                    autoFocus
                    onChange={(e) => setVal(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && save()}
                    onBlur={save}
                    style={{ ...S.input, marginBottom: 0, maxWidth: 220 }}
                />
            ) : (
                <Link to={`/${persona}/sub/${subId}`} style={{ ...S.subLink, color: "#1b2236" }}>
                    {name} <span style={{ color: "#9aa0b2", fontWeight: 400 }}>· 글 {count}</span>
                </Link>
            )}
            <div style={{ display: "flex", gap: 6 }}>
                <Link to={`/${persona}/post/new?sub=${subId}`} style={{ ...S.miniBtn, color: accent }}>
                    + 글
                </Link>
                <button type="button" onClick={() => setEditing((v) => !v)} style={S.miniBtn}>
                    이름
                </button>
                <button
                    type="button"
                    onClick={() => {
                        if (confirm(`"${name}" 소메뉴와 그 안의 글을 삭제할까요?`)) deleteSubMenu(subId);
                    }}
                    style={{ ...S.miniBtn, color: "#dc2626" }}
                >
                    삭제
                </button>
            </div>
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 760, margin: "0 auto", padding: "40px 24px 80px" },
    kicker: { fontSize: 12, fontWeight: 700, letterSpacing: "0.14em" },
    title: { margin: "8px 0 0", fontSize: 30, fontWeight: 800, color: "#1b2236" },
    subtitle: { margin: "10px 0 0", fontSize: 15, color: "#8a90a3", lineHeight: 1.6 },
    addBar: { display: "flex", gap: 8, marginBottom: 28 },
    input: {
        flex: 1, boxSizing: "border-box", padding: "11px 14px", marginBottom: 0,
        borderRadius: 10, border: "1px solid #d8dbe6", fontSize: 14, fontFamily: "inherit",
        background: "#fff", color: "#1b2236",
    },
    addBtn: {
        color: "#fff", border: "none", fontSize: 14, fontWeight: 700, whiteSpace: "nowrap",
        padding: "11px 18px", borderRadius: 10, cursor: "pointer",
    },
    card: {
        background: "#fff", border: "1px solid #eceef5", borderRadius: 16,
        padding: "20px 22px", marginBottom: 16, boxShadow: "0 6px 18px rgba(30,41,90,0.04)",
    },
    cardHead: { display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10 },
    catName: { margin: 0, fontSize: 19, fontWeight: 800, color: "#1b2236" },
    iconBtn: {
        background: "#f5f6fa", border: "none", color: "#6b7280", fontSize: 13, fontWeight: 600,
        padding: "7px 12px", borderRadius: 8, cursor: "pointer",
    },
    delBtn: {
        background: "#fff", border: "1px solid #f3c9c9", color: "#dc2626", fontSize: 13, fontWeight: 600,
        padding: "7px 12px", borderRadius: 8, cursor: "pointer",
    },
    subRow: {
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "10px 14px", background: "#f8f9fc", borderRadius: 10,
    },
    subLink: { textDecoration: "none", fontSize: 15, fontWeight: 600 },
    subAddBtn: {
        background: "#eef0fe", color: "#4f46e5", border: "none", fontSize: 14, fontWeight: 700,
        whiteSpace: "nowrap", padding: "11px 16px", borderRadius: 10, cursor: "pointer",
    },
    miniBtn: {
        background: "none", border: "none", color: "#6b7280", fontSize: 13, fontWeight: 600,
        padding: "4px 8px", borderRadius: 6, cursor: "pointer", textDecoration: "none",
    },
    muted: { color: "#9aa0b2", fontSize: 14 },
};

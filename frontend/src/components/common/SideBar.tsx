import { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { PERSONA_META, resolvePersonaFromPath } from "@/lib/persona";
import PersonaContentMenu from "@/components/common/PersonaContentMenu";
import { useAdmin } from "@/lib/admin";

const linkStyle = (accent: string) => ({ isActive }: { isActive: boolean }) => ({
    padding: "10px 14px",
    textDecoration: "none",
    color: isActive ? accent : "#111",
    fontWeight: isActive ? "bold" : "normal",
});

const subLinkStyle = (accent: string) => ({ isActive }: { isActive: boolean }) => ({
    padding: "8px 14px 8px 28px",
    textDecoration: "none",
    color: isActive ? accent : "#555",
    fontWeight: isActive ? "bold" : "normal",
    fontSize: 14,
});

export default function SideBar({ open = false }: { open?: boolean }) {
    const { pathname } = useLocation();
    const persona = resolvePersonaFromPath(pathname);
    const meta = PERSONA_META[persona];
    const { isAdmin } = useAdmin();

    return (
        <aside
            className={`sidebar${open ? " open" : ""}`}
            style={{
                width: 220,
                borderRight: "1px solid #e5e7eb",
                display: "flex",
                flexDirection: "column",
                height: "100%",
            }}
        >
            <div style={{ flex: 1, padding: 16, overflowY: "auto" }}>
                {/* 페르소나 선택으로 돌아가기 */}
                <NavLink
                    to="/"
                    style={{
                        display: "inline-block",
                        fontSize: 13,
                        color: "#8a90a3",
                        textDecoration: "none",
                        marginBottom: 8,
                    }}
                >
                    ← 페르소나 선택
                </NavLink>

                {/* 현재 페르소나 헤더 */}
                <div
                    style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 8,
                        fontWeight: 700,
                        fontSize: 15,
                        color: meta.accent,
                        marginBottom: 16,
                    }}
                >
                    <span style={{ fontSize: 18 }}>{meta.emoji}</span>
                    <span>{meta.title}</span>
                </div>

                {persona === "athlete" ? (
                    <AthleteMenu accent={meta.accent} />
                ) : (
                    <PersonaContentMenu persona={persona} accent={meta.accent} />
                )}

                {persona === "human" && isAdmin && <HumanLottoMenu accent={meta.accent} />}
            </div>

            {persona === "developer" && (
                <NavLink
                    to="/persona/developer/chat"
                    style={({ isActive }) => ({
                        display: "flex",
                        alignItems: "center",
                        gap: 8,
                        padding: "14px 16px",
                        borderTop: "1px solid #e5e7eb",
                        textDecoration: "none",
                        color: isActive ? meta.accent : "#111",
                        fontWeight: isActive ? 700 : 600,
                        fontSize: 14,
                        flexShrink: 0,
                    })}
                >
                    💬 페르소나 챗
                </NavLink>
            )}
        </aside>
    );
}

function HumanLottoMenu({ accent }: { accent: string }) {
    return (
        <div style={{ marginTop: 24 }}>
            <div style={{ padding: "10px 14px", color: "#111", fontWeight: "bold" }}>
                🎱 로또
            </div>
            <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                <NavLink to="/lottery/lotto/stats" style={subLinkStyle(accent)} end>
                    📊 통계·기록
                </NavLink>
                <NavLink to="/lottery/lotto/create" style={subLinkStyle(accent)}>
                    ➕ 생성
                </NavLink>
            </nav>
        </div>
    );
}

type AthleteSection = "record" | "riding" | "gear";

const ATHLETE_SECTION_PATHS: Record<AthleteSection, string[]> = {
    record: ["/records/body", "/records/health"],
    riding: ["/records/riding", "/plan"],
    gear: ["/bikes"],
};

function sectionForPath(pathname: string): AthleteSection | null {
    for (const [section, prefixes] of Object.entries(ATHLETE_SECTION_PATHS)) {
        if (prefixes.some((p) => pathname.startsWith(p))) return section as AthleteSection;
    }
    return null;
}

function sectionHeaderStyle(open: boolean) {
    return {
        display: "flex",
        alignItems: "center",
        gap: 6,
        width: "100%",
        background: "none",
        border: "none",
        cursor: "pointer",
        padding: "10px 14px",
        font: "inherit",
        fontWeight: "bold" as const,
        fontSize: 15,
        color: open ? "#111" : "#555",
        textAlign: "left" as const,
    };
}

function AthleteMenu({ accent }: { accent: string }) {
    const { isAdmin } = useAdmin();
    const { pathname } = useLocation();

    const [openSection, setOpenSection] = useState<AthleteSection | null>(() =>
        sectionForPath(pathname)
    );

    // 다른 섹션의 페이지로 이동하면 그 섹션이 자동으로 펼쳐지도록 동기화
    useEffect(() => {
        const s = sectionForPath(pathname);
        if (s) setOpenSection(s);
    }, [pathname]);

    const toggle = (section: AthleteSection) =>
        setOpenSection((prev) => (prev === section ? null : section));

    return (
        <>
            <button
                type="button"
                onClick={() => toggle("record")}
                style={sectionHeaderStyle(openSection === "record")}
            >
                <span style={{ fontSize: 11, color: "#9aa0b2" }}>
                    {openSection === "record" ? "▾" : "▸"}
                </span>
                <span>📊 기록</span>
            </button>
            {openSection === "record" && (
                <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                    <div>
                        <NavLink to="/records/body" style={linkStyle(accent)} end>
                            🧍 신체 기록
                        </NavLink>
                        <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                            <NavLink to="/records/body/weight-loss" style={subLinkStyle(accent)}>
                                📉 감량 분석
                            </NavLink>
                            {isAdmin && (
                                <NavLink to="/records/body/health-checkup" style={subLinkStyle(accent)}>
                                    🩺 건강검진
                                </NavLink>
                            )}
                        </nav>
                    </div>

                    {/* 헬스/피트니스 - 하위 메뉴 */}
                    <div>
                        <div style={{ padding: "10px 14px", color: "#111", fontWeight: "bold" }}>
                            🏋️ 피트니스
                        </div>
                        <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                            <NavLink to="/records/health/history" style={subLinkStyle(accent)}>
                                📋 기록
                            </NavLink>
                            <NavLink to="/records/health/log" style={subLinkStyle(accent)}>
                                ✏️ 입력
                            </NavLink>
                        </nav>
                    </div>
                </nav>
            )}

            <button
                type="button"
                onClick={() => toggle("riding")}
                style={{ ...sectionHeaderStyle(openSection === "riding"), marginTop: 8 }}
            >
                <span style={{ fontSize: 11, color: "#9aa0b2" }}>
                    {openSection === "riding" ? "▾" : "▸"}
                </span>
                <span>🚴 라이딩</span>
            </button>
            {openSection === "riding" && (
                <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                    <NavLink to="/records/riding" style={linkStyle(accent)}>
                        📋 라이딩 기록
                    </NavLink>
                    <NavLink to="/plan/brevet" style={linkStyle(accent)}>
                        🏅 랜도너스 계획
                    </NavLink>
                    <NavLink to="/plan/permanent" style={linkStyle(accent)}>
                        🗺️ 퍼머넌트 코스
                    </NavLink>
                    <NavLink to="/plan/touring" style={linkStyle(accent)}>
                        🏕️ 투어링 계획
                    </NavLink>
                    <NavLink to="/plan/live" style={linkStyle(accent)}>
                        🛰️ 라이브 라이딩
                    </NavLink>
                </nav>
            )}

            <button
                type="button"
                onClick={() => toggle("gear")}
                style={{ ...sectionHeaderStyle(openSection === "gear"), marginTop: 8 }}
            >
                <span style={{ fontSize: 11, color: "#9aa0b2" }}>
                    {openSection === "gear" ? "▾" : "▸"}
                </span>
                <span>🚲 장비</span>
            </button>
            {openSection === "gear" && (
                <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                    <NavLink to="/bikes" style={linkStyle(accent)}>
                        🚴 내 자전거
                    </NavLink>
                    <NavLink to="/bikes/register" style={linkStyle(accent)}>
                        ➕ 자전거 등록
                    </NavLink>
                </nav>
            )}
        </>
    );
}

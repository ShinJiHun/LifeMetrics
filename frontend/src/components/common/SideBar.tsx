import { NavLink, useLocation } from "react-router-dom";
import { PERSONA_META, resolvePersonaFromPath } from "@/lib/persona";
import PersonaContentMenu from "@/components/common/PersonaContentMenu";

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

function AthleteMenu({ accent }: { accent: string }) {
    return (
        <>
            <h3>📊 기록</h3>
            <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <div>
                    <NavLink to="/records/body" style={linkStyle(accent)} end>
                        🧍 신체 기록
                    </NavLink>
                    <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                        <NavLink to="/records/body/weight-loss" style={subLinkStyle(accent)}>
                            📉 감량 분석
                        </NavLink>
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

            <h3 style={{ marginTop: 24 }}>🚴 라이딩</h3>
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

            <h3 style={{ marginTop: 24 }}>🚲 장비</h3>
            <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <NavLink to="/bikes" style={linkStyle(accent)}>
                    🚴 내 자전거
                </NavLink>
                <NavLink to="/bikes/register" style={linkStyle(accent)}>
                    ➕ 자전거 등록
                </NavLink>
            </nav>
        </>
    );
}

import { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { PERSONA_META, resolvePersonaFromPath } from "@/lib/persona";
import PersonaContentMenu from "@/components/common/PersonaContentMenu";
import { useAdmin } from "@/lib/admin";

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

// ── 애슬리트 메뉴: 기록(이미 한 것) / 계획(앞으로 할 것) ─────────────────
// 그룹 제목은 고정, 그 아래 섹션만 아코디언으로 하나씩 펼친다.
// 페이지 주소(/records, /plan)는 기존 그대로 두고 메뉴 배치만 정한다.

interface MenuLink {
    to: string;
    label: string;
    end?: boolean;
    adminOnly?: boolean;
}

interface MenuSection {
    key: string;
    label: string;
    adminOnly?: boolean;
    /** 하위 메뉴 없이 바로 이동하는 섹션 */
    to?: string;
    items?: MenuLink[];
}

const ATHLETE_MENU: { label: string; sections: MenuSection[] }[] = [
    {
        label: "📋 기록",
        sections: [
            {key: "checkup", label: "🩺 건강검진", to: "/records/body/health-checkup", adminOnly: true},
            {
                key: "inbody", label: "⚖️ 인바디", items: [
                    {to: "/records/body", label: "인바디 기록", end: true},
                    {to: "/records/body/weight-loss", label: "감량 분석"},
                ],
            },
            {
                key: "fitness", label: "🏋️ 피트니스", items: [
                    {to: "/records/health/history", label: "운동 기록"},
                    {to: "/records/health/log", label: "운동 입력"},
                ],
            },
            {
                key: "riding", label: "🚴 라이딩", items: [
                    {to: "/records/riding", label: "라이딩 기록"},
                    {to: "/plan/live", label: "라이브 라이딩"},
                ],
            },
            {
                key: "gear", label: "🔧 장비", items: [
                    {to: "/bikes", label: "내 자전거", end: true},
                    {to: "/bikes/devices", label: "센서·기기"},
                    {to: "/bikes/register", label: "자전거 등록", adminOnly: true},
                ],
            },
        ],
    },
    {
        label: "🎯 계획",
        sections: [
            {key: "body-goal", label: "⚖️ 체성분 목표", to: "/plan/body-goal"},
            {
                key: "ride-plan", label: "🚴 라이딩 계획", items: [
                    {to: "/plan/brevet", label: "브레베"},
                    {to: "/plan/permanent", label: "퍼머넌트"},
                    {to: "/plan/touring", label: "투어링"},
                ],
            },
            {
                key: "fit-gear", label: "🔧 피팅·장비 계획", items: [
                    {to: "/plan/fitting", label: "피팅 계획"},
                    {to: "/plan/gear", label: "장비 변경 계획"},
                ],
            },
        ],
    },
];

/** 현재 경로가 속한 섹션 (가장 길게 일치하는 메뉴 주소 기준) */
function sectionForPath(pathname: string): string | null {
    let best: { key: string; len: number } | null = null;
    for (const group of ATHLETE_MENU) {
        for (const sec of group.sections) {
            const paths = sec.to ? [sec.to] : (sec.items ?? []).map((i) => i.to);
            for (const p of paths) {
                if ((pathname === p || pathname.startsWith(p + "/")) && (!best || p.length > best.len)) {
                    best = {key: sec.key, len: p.length};
                }
            }
        }
    }
    return best?.key ?? null;
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
        padding: "9px 14px",
        font: "inherit",
        fontWeight: 600,
        fontSize: 14,
        color: open ? "#111" : "#555",
        textAlign: "left" as const,
    };
}

const groupLabelStyle = {
    padding: "6px 14px",
    fontSize: 12,
    fontWeight: 700,
    color: "#9aa0b2",
    letterSpacing: 0.5,
};

function AthleteMenu({ accent }: { accent: string }) {
    const { isAdmin } = useAdmin();
    const { pathname } = useLocation();

    const [openSection, setOpenSection] = useState<string | null>(() => sectionForPath(pathname));

    // 다른 섹션의 페이지로 이동하면 그 섹션이 자동으로 펼쳐지도록 동기화
    useEffect(() => {
        const s = sectionForPath(pathname);
        if (s) setOpenSection(s);
    }, [pathname]);

    const toggle = (key: string) => setOpenSection((prev) => (prev === key ? null : key));

    return (
        <>
            {ATHLETE_MENU.map((group, gi) => (
                <div key={group.label} style={{ marginTop: gi > 0 ? 20 : 0 }}>
                    <div style={groupLabelStyle}>{group.label}</div>
                    {group.sections
                        .filter((sec) => !sec.adminOnly || isAdmin)
                        .map((sec) =>
                            sec.to ? (
                                <NavLink key={sec.key} to={sec.to} style={({ isActive }) => ({
                                    ...sectionHeaderStyle(isActive),
                                    display: "block",
                                    textDecoration: "none",
                                    paddingLeft: 31,
                                    color: isActive ? accent : "#555",
                                })}>
                                    {sec.label}
                                </NavLink>
                            ) : (
                                <div key={sec.key}>
                                    <button
                                        type="button"
                                        onClick={() => toggle(sec.key)}
                                        style={sectionHeaderStyle(openSection === sec.key)}
                                    >
                                        <span style={{ fontSize: 11, color: "#9aa0b2" }}>
                                            {openSection === sec.key ? "▾" : "▸"}
                                        </span>
                                        <span>{sec.label}</span>
                                    </button>
                                    {openSection === sec.key && (
                                        <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                                            {(sec.items ?? [])
                                                .filter((item) => !item.adminOnly || isAdmin)
                                                .map((item) => (
                                                    <NavLink key={item.to} to={item.to} end={item.end}
                                                             style={subLinkStyle(accent)}>
                                                        {item.label}
                                                    </NavLink>
                                                ))}
                                        </nav>
                                    )}
                                </div>
                            )
                        )}
                </div>
            ))}
        </>
    );
}

// 경로 기반 페르소나 판별 + 페르소나별 메타 (SideBar / blog 페이지 공용)
import type { Persona } from "@/types/content";

export type PersonaKey = "athlete" | Persona;

export function resolvePersonaFromPath(pathname: string): PersonaKey {
    if (pathname.startsWith("/developer") || pathname.startsWith("/persona")) return "developer";
    if (pathname.startsWith("/human")) return "human";
    if (pathname.startsWith("/stock")) return "stock";
    if (pathname.startsWith("/lottery")) return "lottery";
    return "athlete";
}

export const PERSONA_META: Record<PersonaKey, { emoji: string; title: string; accent: string }> = {
    athlete: { emoji: "🚴", title: "애슐리트 신지훈", accent: "#2563eb" },
    developer: { emoji: "👨‍💻", title: "개발자 신지훈", accent: "#6366f1" },
    human: { emoji: "🌱", title: "인간 신지훈", accent: "#16a34a" },
    stock: { emoji: "📈", title: "증권 신지훈", accent: "#d97706" },
    lottery: { emoji: "🎰", title: "복권 신지훈", accent: "#7c3aed" },
};

// blog 페이지에서 쓰는 좁은 타입 (athlete 제외)
export function blogPersonaFromPath(pathname: string): Persona {
    if (pathname.startsWith("/human")) return "human";
    if (pathname.startsWith("/stock")) return "stock";
    return "developer";
}

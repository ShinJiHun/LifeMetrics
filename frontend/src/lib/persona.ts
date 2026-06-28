// 경로 기반 페르소나 판별 + 페르소나별 메타 (SideBar / blog 페이지 공용)
import type { Persona } from "@/types/content";

export type PersonaKey = "athlete" | Persona;

export function resolvePersonaFromPath(pathname: string): PersonaKey {
    if (pathname.startsWith("/developer") || pathname.startsWith("/persona")) return "developer";
    if (pathname.startsWith("/human")) return "human";
    return "athlete";
}

export const PERSONA_META: Record<PersonaKey, { emoji: string; title: string; accent: string }> = {
    athlete: { emoji: "🚴", title: "애슐리트 신지훈", accent: "#2563eb" },
    developer: { emoji: "👨‍💻", title: "개발자 신지훈", accent: "#6366f1" },
    human: { emoji: "🌱", title: "인간 신지훈", accent: "#16a34a" },
};

// blog 페이지에서 쓰는 좁은 타입 (athlete 제외)
export function blogPersonaFromPath(pathname: string): Persona {
    return pathname.startsWith("/human") ? "human" : "developer";
}

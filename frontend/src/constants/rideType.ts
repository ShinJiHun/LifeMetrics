// 라이딩 타입: DB에는 영문 코드로 저장, 화면에는 한글 라벨 표시
export const RIDE_TYPES: Array<{ code: string; label: string }> = [
    {code: "GENERAL", label: "일반 라이딩"},
    {code: "PERMANENT", label: "퍼머넌트"},
    {code: "BREVET", label: "브레베"},
    {code: "FLECHE", label: "플레쉬"},
    {code: "POPULAIRE", label: "포퓨레어"},
    {code: "TOURING", label: "투어링"},
];

export const RIDE_TYPE_LABEL: Record<string, string> = Object.fromEntries(
    RIDE_TYPES.map((t) => [t.code, t.label])
);

export const RIDE_TYPE_COLOR: Record<string, string> = {
    GENERAL: "#475569",
    PERMANENT: "#2563eb",
    BREVET: "#f59e0b",
    FLECHE: "#8b5cf6",
    POPULAIRE: "#10b981",
    TOURING: "#ec4899",
};

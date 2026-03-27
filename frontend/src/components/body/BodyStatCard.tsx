type Props = {
    label: string;
    value: number;
    unit: string;
    delta?: number | null;
    active?: boolean;
    onClick?: () => void;
};

export default function BodyStatCard({
                                         label,
                                         value,
                                         unit,
                                         delta,
                                         active,
                                         onClick,
                                     }: Props) {
    const hasDelta = typeof delta === "number";
    const isUp = hasDelta && delta > 0;

    return (
        <div
            onClick={onClick}
            style={{
                padding: 16,
                borderRadius: 14,
                border: active ? "2px solid #2563eb" : "1px solid #e5e7eb",
                cursor: "pointer",
                background: "#fff",
            }}
        >
            <div style={{ fontSize: 13, color: "#6b7280" }}>{label}</div>

            <div style={{ fontSize: 28, fontWeight: 700 }}>
                {value}
                <span style={{ fontSize: 14, marginLeft: 4 }}>{unit}</span>
            </div>

            {hasDelta && (
                <div
                    style={{
                        marginTop: 6,
                        fontSize: 13,
                        color: isUp ? "#ef4444" : "#10b981",
                    }}
                >
                    {isUp ? "▲" : "▼"} {Math.abs(delta).toFixed(2)}
                </div>
            )}
        </div>
    );
}

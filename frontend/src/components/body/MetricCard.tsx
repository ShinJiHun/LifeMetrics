// src/components/MetricCard.tsx

interface MetricCardProps {
    title: string;
    value: number | null;   // ✅ 수정
    unit?: string;
    delta?: number | null;
    active?: boolean;
    onClick?: () => void;
}

export default function MetricCard({
                                       title,
                                       value,
                                       unit,
                                       delta,
                                       active,
                                       onClick,
                                   }: MetricCardProps) {
    return (
        <div
            className={`metric-card ${active ? "active" : ""}`}
            onClick={onClick}
        >
            <div className="metric-title">{title}</div>

            <div className="metric-value">
                {value != null ? value : "-"}
                {unit && <span className="metric-unit"> {unit}</span>}
            </div>

            {typeof delta === "number" && (
                <div className={`metric-delta ${delta >= 0 ? "plus" : "minus"}`}>
                    {/* 서버에서 뺄셈으로 만든 값이라 3.5999999999999943 처럼 오차가 남는다. */}
                    {delta >= 0 ? "▲" : "▼"} {Number(Math.abs(delta).toFixed(1))}
                </div>
            )}
        </div>
    );
}

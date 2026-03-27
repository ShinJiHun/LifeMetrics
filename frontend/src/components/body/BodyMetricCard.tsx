interface MetricCardProps {
    title: string;
    value: number | null;
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
                {value !== null ? value : "-"}
                {unit && <span className="metric-unit"> {unit}</span>}
            </div>

            {typeof delta === "number" && (
                <div className={`metric-delta ${delta >= 0 ? "plus" : "minus"}`}>
                    {delta >= 0 ? "▲" : "▼"} {Math.abs(delta)}
                </div>
            )}
        </div>
    );
}

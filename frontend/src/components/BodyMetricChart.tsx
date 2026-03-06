import { useState } from "react";  // useRef 제거
import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ReferenceArea,
} from "recharts";
import type { MetricKey } from "@/types/MetricKey";

interface Props {
    data: any[];
    metricKey: MetricKey;
    onDateClick?: (recordDate: string) => void;
}

const domainMap: Record<MetricKey, [number, number]> = {
    weight: [50, 100],
    skeletalMuscleMass: [25, 45],
    bodyFatMass: [10, 40],
    bodyFatPercentage: [5, 40],
    bmi: [15, 35],
    visceralFatLevel: [1, 20],
};

const unitMap: Record<MetricKey, string> = {
    weight: "kg",
    skeletalMuscleMass: "kg",
    bodyFatMass: "kg",
    bodyFatPercentage: "%",
    bmi: "",
    visceralFatLevel: "",
};

const titleMap: Record<MetricKey, string> = {
    weight: "체중",
    skeletalMuscleMass: "골격근량",
    bodyFatMass: "체지방량",
    bodyFatPercentage: "체지방률",
    bmi: "BMI",
    visceralFatLevel: "내장지방",
};

export default function BodyMetricChart({ data, metricKey, onDateClick }: Props) {
    const chartData = data
        .filter((d) => d[metricKey] != null)
        .map((d) => ({
            date: d.recordDate,
            value: d[metricKey],
        }));

    const yDomain = domainMap[metricKey];
    const unit = unitMap[metricKey];

    // ✅ 줌 상태
    const [zoomData, setZoomData] = useState(chartData);
    const [refAreaLeft, setRefAreaLeft] = useState<string | null>(null);
    const [refAreaRight, setRefAreaRight] = useState<string | null>(null);
    const [isSelecting, setIsSelecting] = useState(false);

    // ✅ 드래그 시작
    const handleMouseDown = (e: any) => {
        if (e?.activeLabel) {
            setRefAreaLeft(e.activeLabel);
            setIsSelecting(true);
        }
    };

    // ✅ 드래그 중
    const handleMouseMove = (e: any) => {
        if (isSelecting && e?.activeLabel) {
            setRefAreaRight(e.activeLabel);
        }
    };

    // ✅ 드래그 끝 - 줌 적용
    const handleMouseUp = () => {
        if (refAreaLeft && refAreaRight) {
            const leftIndex = chartData.findIndex((d) => d.date === refAreaLeft);
            const rightIndex = chartData.findIndex((d) => d.date === refAreaRight);

            if (leftIndex !== -1 && rightIndex !== -1) {
                const [start, end] = leftIndex < rightIndex
                    ? [leftIndex, rightIndex]
                    : [rightIndex, leftIndex];

                if (end - start >= 1) {
                    setZoomData(chartData.slice(start, end + 1));
                }
            }
        }

        setRefAreaLeft(null);
        setRefAreaRight(null);
        setIsSelecting(false);
    };

    // ✅ 줌 리셋
    const handleZoomReset = () => {
        setZoomData(chartData);
    };

    const handleDotClick = (_: unknown, payload: any) => {
        if (onDateClick && payload?.payload?.date) {
            onDateClick(payload.payload.date);
        }
    };

    const pointWidth = 40;
    const chartWidth = Math.max(zoomData.length * pointWidth, 600);
    const isZoomed = zoomData.length !== chartData.length;

    return (
        <div style={{ width: "100%", height: 310 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                <h4 style={{ margin: 0 }}>{titleMap[metricKey]}</h4>

                {/* ✅ 줌 컨트롤 */}
                <div style={{ display: "flex", gap: 8, fontSize: 12 }}>
                    {isZoomed && (
                        <button
                            onClick={handleZoomReset}
                            style={{
                                padding: "4px 8px",
                                border: "1px solid #e5e7eb",
                                borderRadius: 4,
                                background: "#fff",
                                cursor: "pointer",
                            }}
                        >
                            🔄 전체 보기
                        </button>
                    )}
                    <span style={{ color: "#6b7280" }}>
                        드래그하여 확대
                    </span>
                </div>
            </div>

            <div
                style={{
                    position: "relative",
                    width: "100%",
                    height: 280,
                    border: "1px solid #e5e7eb",
                    borderRadius: 8,
                    overflow: "hidden",
                }}
            >
                <div
                    style={{
                        width: "100%",
                        height: "100%",
                        overflowX: "auto",
                        overflowY: "hidden",
                    }}
                >
                    <LineChart
                        width={chartWidth}
                        height={270}
                        data={zoomData}
                        margin={{
                            top: 16,
                            right: 20,
                            bottom: 24,
                            left: 10,
                        }}
                        onMouseDown={handleMouseDown}
                        onMouseMove={handleMouseMove}
                        onMouseUp={handleMouseUp}
                        onMouseLeave={handleMouseUp}
                    >
                        <CartesianGrid strokeDasharray="3 3" />

                        <XAxis
                            dataKey="date"
                            tick={{ fontSize: 11 }}
                            interval={0}
                            tickFormatter={(value: string) => {
                                const d = new Date(value);
                                const yy = String(d.getFullYear()).slice(2);
                                const mm = String(d.getMonth() + 1).padStart(2, "0");
                                return `${yy}-${mm}`;
                            }}
                        />

                        <YAxis
                            domain={yDomain}
                            width={40}
                            tick={{ fontSize: 12 }}
                            axisLine={false}
                            tickLine={false}
                        />

                        <Tooltip
                            formatter={(value) =>
                                typeof value === "number"
                                    ? `${value.toFixed(1)} ${unit}`
                                    : value
                            }
                            labelFormatter={(label) => `측정일: ${label}`}
                        />

                        <Line
                            type="monotone"
                            dataKey="value"
                            stroke="#2563eb"
                            strokeWidth={2}
                            dot={{ r: 4, cursor: "pointer" }}
                            activeDot={{
                                r: 8,
                                cursor: "pointer",
                                onClick: handleDotClick,
                            }}
                        />

                        {/* ✅ 선택 영역 표시 */}
                        {isSelecting && refAreaLeft && refAreaRight && (
                            <ReferenceArea
                                x1={refAreaLeft}
                                x2={refAreaRight}
                                strokeOpacity={0.3}
                                fill="#2563eb"
                                fillOpacity={0.2}
                            />
                        )}
                    </LineChart>
                </div>
            </div>
        </div>
    );
}

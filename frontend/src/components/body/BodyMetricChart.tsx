import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
} from "recharts";
import type { MetricKey } from "@/types/MetricKey.ts";

interface Props {
    data: any[];
    metricKey: MetricKey;
    measurementType?: "FITDAYS" | "INBODY";
    onDateClick?: (recordDate: string) => void;
}

type DomainConfig = {
    FITDAYS: [number, number];
    INBODY:  [number, number];
    tickCount: number;
};

const domainMap: Record<MetricKey, DomainConfig> = {
    weight: {
        FITDAYS: [40, 120],
        INBODY:  [40, 120],
        tickCount: 9,
    },
    skeletalMuscleMass: {
        FITDAYS: [20, 55],
        INBODY:  [25, 45],
        tickCount: 8,
    },
    bodyFatMass: {
        FITDAYS: [5, 45],
        INBODY:  [5, 40],
        tickCount: 9,
    },
    bodyFatPercentage: {
        FITDAYS: [5, 45],
        INBODY:  [5, 40],
        tickCount: 9,
    },
    bmi: {
        FITDAYS: [15, 35],
        INBODY:  [15, 35],
        tickCount: 5,
    },
    visceralFatLevel: {
        FITDAYS: [1, 20],
        INBODY:  [1, 20],
        tickCount: 10,
    },
};

const unitMap: Record<MetricKey, string> = {
    weight:             "kg",
    skeletalMuscleMass: "kg",
    bodyFatMass:        "kg",
    bodyFatPercentage:  "%",
    bmi:                "",
    visceralFatLevel:   "",
};

const titleMap: Record<MetricKey, string> = {
    weight:             "체중",
    skeletalMuscleMass: "골격근량",
    bodyFatMass:        "체지방량",
    bodyFatPercentage:  "체지방률",
    bmi:                "BMI",
    visceralFatLevel:   "내장지방",
};

const lineColorMap: Record<string, string> = {
    FITDAYS: "#10b981",
    INBODY:  "#8b5cf6",
};

export default function BodyMetricChart({ data, metricKey, measurementType = "FITDAYS", onDateClick }: Props) {
    const chartData = data
        .filter((d) => d[metricKey] != null)
        .map((d) => ({
            date:  d.recordDate,
            value: d[metricKey],
        }));

    const domainCfg = domainMap[metricKey];
    const yDomain   = domainCfg[measurementType];
    const unit      = unitMap[metricKey];
    const lineColor = lineColorMap[measurementType];

    const handleDotClick = (_: unknown, payload: any) => {
        if (onDateClick && payload?.payload?.date) {
            onDateClick(payload.payload.date);
        }
    };

    // 데이터 수에 따라 차트 너비 계산 (최소 600px)
    const pointWidth = 48;
    const chartWidth = Math.max(chartData.length * pointWidth, 600);

    return (
        <div style={{ width: "100%", height: 310 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                <h4 style={{ margin: 0 }}>{titleMap[metricKey]}</h4>
                <span style={{ color: "#9ca3af", fontSize: 12 }}>
                    총 {chartData.length}개
                </span>
            </div>

            {/* 좌우 스크롤 컨테이너 */}
            <div style={{
                width: "100%",
                height: 280,
                border: "1px solid #e5e7eb",
                borderRadius: 8,
                overflowX: "auto",
                overflowY: "hidden",
            }}>
                <LineChart
                    width={chartWidth}
                    height={270}
                    data={chartData}
                    margin={{ top: 16, right: 20, bottom: 24, left: 10 }}
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
                            const dd = String(d.getDate()).padStart(2, "0");
                            return `${yy}-${mm}-${dd}`;
                        }}
                    />

                    <YAxis
                        domain={yDomain}
                        tickCount={domainCfg.tickCount}
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
                        stroke={lineColor}
                        strokeWidth={2}
                        dot={{ r: 4, cursor: "pointer" }}
                        activeDot={{
                            r: 8,
                            cursor: "pointer",
                            onClick: handleDotClick,
                        }}
                    />
                </LineChart>
            </div>
        </div>
    );
}

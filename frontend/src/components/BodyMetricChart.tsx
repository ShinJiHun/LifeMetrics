import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
} from "recharts";
import type { MetricKey } from "@/types/MetricKey";

interface Props {
    data: any[];
    metricKey: MetricKey;
    title: string;
}

/** 지표별 Y축 범위 */
const domainMap: Record<MetricKey, [number, number]> = {
    weight: [50, 100],
    skeletalMuscleMass: [25, 45],
    bodyFatMass: [10, 40],
    bodyFatPercentage: [5, 40],
    bmi: [15, 35],
    visceralFatLevel: [1, 20],
};

/** 지표별 단위 */
const unitMap: Record<MetricKey, string> = {
    weight: "kg",
    skeletalMuscleMass: "kg",
    bodyFatMass: "kg",
    bodyFatPercentage: "%",
    bmi: "",
    visceralFatLevel: "",
};

export default function BodyMetricChart({
                                            data,
                                            metricKey,
                                            title,
                                        }: Props) {
    const chartData = data
        .filter((d) => d[metricKey] != null)
        .map((d) => ({
            date: d.recordDate,   // ✅ camelCase
            value: d[metricKey],
        }));

    const yDomain = domainMap[metricKey];
    const unit = unitMap[metricKey];

    const titleMap: Record<MetricKey, string> = {
        weight: "체중",
        skeletalMuscleMass: "골격근량",
        bodyFatMass: "체지방량",
        bodyFatPercentage: "체지방률",
        bmi: "BMI",
        visceralFatLevel: "내장지방",
    };

    return (
        <div style={{width: "100%", height: 320}}>
            <h4 style={{marginBottom: 8}}>
                {titleMap[metricKey]}
            </h4>

            <ResponsiveContainer width="100%" height="100%">
                <LineChart
                    data={chartData}
                    margin={{
                        top: 16,
                        right: 5,
                        bottom: 24,
                        left: -30,
                    }}
                >
                    <CartesianGrid strokeDasharray="3 3"/>

                    <XAxis
                        dataKey="date"
                        tick={{fontSize: 12}}
                        interval="preserveStartEnd"
                        tickFormatter={(value: string) => {
                            const d = new Date(value);
                            const yy = String(d.getFullYear()).slice(2);
                            const mm = String(d.getMonth() + 1).padStart(2, "0");
                            return `${yy}-${mm}`;
                        }}
                    />

                    <YAxis
                        domain={yDomain}
                        width={56}
                        tick={{fontSize: 12}}
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
                        dot={{r: 3}}
                        activeDot={{r: 6}}
                    />
                </LineChart>
            </ResponsiveContainer>
        </div>
    );
}

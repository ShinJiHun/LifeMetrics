import { LineChart, Line, ResponsiveContainer } from "recharts";

export default function MiniLineChart({
                                          data,
                                          dataKey,
                                          color,
                                      }: {
    data: any[];
    dataKey: string;
    color: string;
}) {
    return (
        <ResponsiveContainer width="100%" height={260}>
            <LineChart data={data}>
                <Line
                    type="monotone"
                    dataKey={dataKey}
                    stroke={color}
                    strokeWidth={2.5}
                    dot={{ r: 4 }}
                />
            </LineChart>
        </ResponsiveContainer>
    );
}

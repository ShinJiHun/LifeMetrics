// BodySummary.tsx
import type { BodyRecord } from "../types/BodyRecord";

export default function BodySummary({ record }: { record: BodyRecord }) {
  return (
    <div style={{ width: 320, border: "1px solid #ddd", padding: 16 }}>
      <h3>📋 요약</h3>
      <p>체중: {record.weight} kg</p>
      <p>체지방률: {record.body_fat_percentage} %</p>
      <p>ECW/TBW: {record.ecw_tbw_ratio}</p>
    </div>
  );
}

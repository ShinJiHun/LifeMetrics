// BodyChart.tsx
import type { BodyRecord } from "../types/BodyRecord";

export default function BodyChart({ records }: { records: BodyRecord[] }) {
  return (
    <div>
      <h3>📈 변화 추이</h3>
      <ul>
        {records.map(r => (
          <li key={r.recordDate}>
            {r.recordDate} / {r.weight}kg / {r.bodyFatPercentage}%
          </li>
        ))}
      </ul>
    </div>
  );
}

// BodyChart.tsx
import type { BodyRecord } from "../types/BodyRecord";

export default function BodyChart({ records }: { records: BodyRecord[] }) {
  return (
    <div>
      <h3>📈 변화 추이</h3>
      <ul>
        {records.map(r => (
          <li key={r.record_date}>
            {r.record_date} / {r.weight}kg / {r.body_fat_percentage}%
          </li>
        ))}
      </ul>
    </div>
  );
}

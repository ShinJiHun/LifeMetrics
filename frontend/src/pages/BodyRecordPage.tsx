import { useState } from "react";
import { useBodyRecords } from "../hooks/useBodyRecords";
import BodySummary from "../components/BodySummary";
import BodyChart from "../components/BodyChart";
import HumanModelPreview from "../components/HumanModelPreview";
import type { BodyRecord } from "../types/BodyRecord";

export default function BodyRecordPage() {
  const { records, loading } = useBodyRecords();
  const [selected, setSelected] = useState<BodyRecord | null>(null);

  if (loading) return <p>로딩 중...</p>;
  if (!records.length) return <p>기록이 없습니다.</p>;

  const current = selected ?? records[0];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
      <h2>🧍 신체 기록</h2>

      <select
        value={current.record_date}
        onChange={(e) =>
          setSelected(
            records.find(r => r.record_date === e.target.value) || null
          )
        }
      >
        {records.map(r => (
          <option key={r.record_date} value={r.record_date}>
            {r.record_date}
          </option>
        ))}
      </select>

      <div style={{ display: "flex", gap: 24 }}>
        <BodySummary record={current} />
        <HumanModelPreview record={current} />
      </div>

      <BodyChart records={records} />
    </div>
  );
}

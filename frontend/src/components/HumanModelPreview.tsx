// HumanModelPreview.tsx
import type { BodyRecord } from "../types/BodyRecord";

export default function HumanModelPreview({ record }: { record: BodyRecord }) {
  return (
    <div style={{ width: 240, border: "1px dashed #aaa", padding: 16 }}>
      <h3>🧍 모델</h3>
      <p>체지방률: {record.body_fat_percentage}%</p>
    </div>
  );
}

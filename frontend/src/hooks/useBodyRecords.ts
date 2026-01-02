import { useEffect, useState } from "react";
import type { BodyRecord } from "../types/BodyRecord";

export function useBodyRecords() {
  const [records, setRecords] = useState<BodyRecord[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchRecords() {
      setLoading(true);

      // 🔹 mock (나중에 FastAPI로 교체)
      const mockData: BodyRecord[] = [
        {
          record_date: "2025-01-05",
          weight: 78.2,
          weight_ma: 78.9,
          weight_delta: -0.7,
          body_fat_percentage: 21.5,
          body_fat_ma: 22.1,
          body_fat_delta: -0.6,
          ecw_tbw_ratio: 0.382,
        },
        {
          record_date: "2024-12-20",
          weight: 79.5,
          weight_ma: 79.8,
          weight_delta: 0.3,
          body_fat_percentage: 22.5,
          body_fat_ma: 22.8,
          body_fat_delta: 0.4,
          ecw_tbw_ratio: 0.386,
        },
      ];

      setRecords(mockData);
      setLoading(false);
    }

    fetchRecords();
  }, []);

  return { records, loading };
}

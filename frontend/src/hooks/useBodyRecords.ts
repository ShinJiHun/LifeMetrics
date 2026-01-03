import { useEffect, useState } from "react";
import { fetchBodyRecords } from "@/api/body";
import type { BodySummaryRecord } from "@/types/BodySummaryRecord";

export function useBodyRecords(userId = 1) {
    const [records, setRecords] = useState<BodySummaryRecord[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        async function load() {
            setLoading(true);
            const data = await fetchBodyRecords(userId);

            // ✅ 이미 완성된 SummaryRecord
            setRecords(data.records as BodySummaryRecord[]);
            setLoading(false);
        }

        load();
    }, [userId]);

    return { records, loading };
}

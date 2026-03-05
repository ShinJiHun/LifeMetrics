import { useEffect, useState, useCallback } from "react";
import { fetchBodyRecords } from "@/api/body";
import type { BodySummaryRecord } from "@/types/BodySummaryRecord";

export function useBodyRecords(userId = 1) {
    const [records, setRecords] = useState<BodySummaryRecord[]>([]);
    const [loading, setLoading] = useState(true);

    const load = useCallback(async () => {
        setLoading(true);
        const data = await fetchBodyRecords(userId);
        setRecords(data.records as BodySummaryRecord[]);
        setLoading(false);
    }, [userId]);

    useEffect(() => {
        load();
    }, [load]);

    return { records, loading, refetch: load };
}
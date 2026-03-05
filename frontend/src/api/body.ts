import type { BodyRecordsResponse } from "@/types/BodyRecordsResponse";

export async function fetchBodyRecords(
    userId = 1
): Promise<BodyRecordsResponse> {
    const res: Response = await fetch(
        `/api/body/records?userId=${userId}`
    );

    if (!res.ok) {
        throw new Error("Failed to fetch body records");
    }
    return res.json();
}

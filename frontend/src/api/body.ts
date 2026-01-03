import type { BodyRecordsResponse } from "@/types/BodyRecordsResponse";

const API_BASE = import.meta.env.VITE_API_BASE;

export async function fetchBodyRecords(
    userId = 1
): Promise<BodyRecordsResponse> {

    console.log("🔥 API_BASE =", API_BASE);


    const res: Response = await fetch(
        `${API_BASE}/api/body/records?userId=${userId}`
    );

    if (!res.ok) {
        throw new Error("Failed to fetch body records");
    }
    return res.json();
}

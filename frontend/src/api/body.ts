import type { BodyRecordsResponse } from "@/types/BodyRecordsResponse";
import type { WeightLossAnalysis } from "@/types/WeightLossAnalysis";

const API_BASE = import.meta.env.VITE_API_BASE || "";

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

/**
 * 감량 분석. 인바디 기록이나 파워 데이터가 없으면 서버가 422 와 안내 문구를 준다.
 */
export async function fetchWeightLossAnalysis(
    userId = 1
): Promise<WeightLossAnalysis> {
    const res = await fetch(`${API_BASE}/api/body/weight-loss?userId=${userId}`);

    if (res.status === 422) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message ?? "분석에 필요한 데이터가 부족합니다.");
    }
    if (!res.ok) {
        throw new Error("감량 분석을 불러오지 못했습니다.");
    }
    return res.json();
}

/** 감량 분석 AI 코칭. 호출 비용이 있어 버튼으로만 부른다. */
export async function fetchWeightLossNarrative(userId = 1): Promise<string> {
    const res = await fetch(
        `${API_BASE}/api/body/weight-loss/ai?userId=${userId}`,
        { method: "POST" }
    );

    if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        if (body.code === "ADMIN_REQUIRED") {
            throw new Error("관리자 비밀번호로 로그인하여 시도해주세요.");
        }
        throw new Error(body.error ?? body.message ?? "AI 분석에 실패했습니다.");
    }
    return (await res.json()).narrative;
}
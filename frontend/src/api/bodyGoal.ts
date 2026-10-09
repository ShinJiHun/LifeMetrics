export type GoalSource = "INBODY" | "FORMULA" | "MANUAL";
export type GoalStatus = "ACTIVE" | "ACHIEVED" | "ABANDONED";

export interface BodyGoal {
    id?: number;
    status?: GoalStatus;
    source: GoalSource;
    baseRecordId: number | null;
    startDate: string;              // YYYY-MM-DD
    startWeight: number | null;
    startFatMass: number | null;
    startMuscleMass: number | null; // 골격근량
    targetWeight: number | null;
    targetFatMass: number | null;
    targetMuscleMass: number | null;
    targetDate: string | null;
    notes: string | null;
}

export interface GoalSuggestion {
    source: "INBODY" | "FORMULA";
    baseRecordId: number;
    baseRecordDate: string;
    startWeight: number;
    startFatMass: number;
    startMuscleMass: number | null;
    targetWeight: number;
    targetFatMass: number;
    targetMuscleMass: number | null;
    targetDate: string | null;
    heightCm: number | null;
    standardWeight: number | null;
    weeksAtHalfPercent: number | null;
    weeksAtOnePercent: number | null;
    basis: string;
}

export interface GoalStatusView {
    goal: BodyGoal;
    current: {
        weightDate: string | null;
        weight: number | null;
        inbodyDate: string | null;
        fatMass: number | null;
        muscleMass: number | null;
    };
    progress: {
        weightPct: number | null;
        fatPct: number | null;
        musclePct: number | null;
        daysElapsed: number;
        daysLeft: number | null;
    };
    ftp: number | null;
    ftpEstimated: boolean;
    wkgStart: number | null;
    wkgCurrent: number | null;
    wkgTarget: number | null;
}

export const SOURCE_LABEL: Record<GoalSource, string> = {
    INBODY: "인바디 기록지",
    FORMULA: "계산",
    MANUAL: "직접 입력",
};

async function errorMessage(res: Response, fallback: string): Promise<string> {
    const body = await res.json().catch(() => ({}));
    if (body.code === "ADMIN_REQUIRED") return "관리자 비밀번호로 로그인하여 시도해주세요.";
    return body.message ?? body.error ?? fallback;
}

/** 진행 중인 목표가 없으면 null */
export async function fetchCurrentGoal(userId = 1): Promise<GoalStatusView | null> {
    const res = await fetch(`/api/body/goals/current?userId=${userId}`);
    if (res.status === 204) return null;
    if (!res.ok) throw new Error(await errorMessage(res, "목표를 불러오지 못했습니다."));
    return res.json();
}

export async function fetchGoals(userId = 1): Promise<BodyGoal[]> {
    const res = await fetch(`/api/body/goals?userId=${userId}`);
    if (!res.ok) throw new Error(await errorMessage(res, "목표 이력을 불러오지 못했습니다."));
    return res.json();
}

export async function fetchGoalSuggestion(userId = 1): Promise<GoalSuggestion> {
    const res = await fetch(`/api/body/goals/suggestion?userId=${userId}`);
    if (!res.ok) throw new Error(await errorMessage(res, "목표 제안을 계산하지 못했습니다."));
    return res.json();
}

/** id 가 없으면 새 목표(기존 진행 중 목표는 서버에서 중단 처리) */
export async function saveGoal(goal: BodyGoal, userId = 1): Promise<BodyGoal> {
    const res = await fetch(goal.id ? `/api/body/goals/${goal.id}` : `/api/body/goals?userId=${userId}`, {
        method: goal.id ? "PUT" : "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(goal),
    });
    if (!res.ok) throw new Error(await errorMessage(res, `저장 실패 (${res.status})`));
    return res.json();
}

/** AI 코칭. 호출 비용이 있어 버튼으로만 부른다. */
export async function fetchGoalNarrative(userId = 1): Promise<string> {
    const res = await fetch(`/api/body/goals/ai?userId=${userId}`, {method: "POST"});
    if (!res.ok) throw new Error(await errorMessage(res, "AI 분석에 실패했습니다."));
    return (await res.json()).narrative;
}

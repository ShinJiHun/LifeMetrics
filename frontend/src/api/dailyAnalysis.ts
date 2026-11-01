// src/api/dailyAnalysis.ts
//
// 하루치 AI 분석(관리자 전용). 매일 01:30(KST)에 서버가 전날 분을 만든다.
import { isAxiosError } from "axios";
import api from "@/lib/axios";

export interface DailyAnalysisResult {
    summary?: string | null;
    activity?: string | null;
    strength?: string | null;
    nutrition?: string | null;
    body?: string | null;
    goodPoints?: string[] | null;
    improvements?: string[] | null;
    dataNotes?: string[] | null;
}

export interface DailyAnalysisInput {
    dailyActivity: { steps: number | null; activeKcal: number | null; distanceM: number | null }[];
    meals: { calories: number | null; proteinG: number | null }[];
    fitness: { totalVolumeKg: number | null; totalSets: number | null }[];
    rides: { distanceM: number | null; movingTimeSec: number | null }[];
    bodyRecords: { weightKg: number | null; bodyFatPercent: number | null }[];
}

export interface DailyAnalysis {
    date: string;
    analyzedAt: string;
    input: DailyAnalysisInput;
    result: DailyAnalysisResult;
}

export interface DailyAnalysisResponse {
    date: string;
    analysis: DailyAnalysis | null;
}

function toError(e: unknown, fallback: string): Error {
    if (isAxiosError(e) && e.response?.status === 403) return new Error("관리자 로그인이 필요합니다.");
    if (isAxiosError(e) && e.response?.data?.message) return new Error(e.response.data.message);
    return new Error(fallback);
}

export async function fetchDailyAnalysis(date: string): Promise<DailyAnalysisResponse> {
    try {
        return (await api.get<DailyAnalysisResponse>("/ai/analysis/daily", { params: { date } })).data;
    } catch (e) {
        throw toError(e, "분석을 불러오지 못했습니다.");
    }
}

/** 지금 다시 분석한다. 그날 기록이 없으면 analysis 가 null 이다. */
export async function runDailyAnalysis(date: string): Promise<DailyAnalysisResponse> {
    try {
        return (await api.post<DailyAnalysisResponse>(`/ai/analysis/daily/${date}`)).data;
    } catch (e) {
        throw toError(e, "분석에 실패했습니다.");
    }
}

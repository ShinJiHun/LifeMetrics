import { useEffect, useState } from "react";
import api from "@/lib/axios";

/** 작업 대상 저장소 — 대화의 첫 메시지에서 고르고 이어지는 메시지가 물려받는다 */
export type DevRepo = "lifemetrics" | "data_pipeline";
export const DEV_REPOS: { value: DevRepo; label: string; hint: string }[] = [
    { value: "lifemetrics", label: "LifeMetrics 앱", hint: "백엔드·화면 — dev 배포 시 개발 서버에 올림" },
    { value: "data_pipeline", label: "data_pipeline", hint: "파이썬 파이프라인 — dev 배포는 문법 검사만, 운영 반영 시 main 갱신" },
];

/** 개발 요청함은 개발 서버 전용(백엔드 DEV_REQUESTS_ENABLED). 운영에서는 메뉴를 숨긴다. */
let enabledCache: Promise<boolean> | null = null;
export function useDevRequestsEnabled(check = true): boolean {
    const [enabled, setEnabled] = useState(false);
    useEffect(() => {
        if (!check) return;
        enabledCache ??= api
            .get<{ enabled: boolean }>("/dev-requests/enabled")
            .then((r) => !!r.data?.enabled)
            .catch(() => false);
        let alive = true;
        enabledCache.then((v) => alive && setEnabled(v));
        return () => {
            alive = false;
        };
    }, [check]);
    return enabled;
}

/** 서버 DevRequestService.DevRequest 와 1:1. 시간은 "YYYY-MM-DDTHH:mm:ss". */
export interface DevRequest {
    id: number;
    parentId: number | null;
    /** MESSAGE: 내 메시지 → Claude 답 / DEPLOY: 대화 브랜치를 빌드해 개발 서버 배포 */
    kind: "MESSAGE" | "DEPLOY";
    rootId: number | null;
    repo: DevRepo;
    title: string;
    body: string;
    status: DevRequestStatus;
    branch: string | null;
    commitSha: string | null;
    sessionId: string | null;
    summary: string | null;
    diffStat: string | null;
    logTail: string | null; // 목록에서는 null
    error: string | null;
    costUsd: number | null;
    createdAt: string;
    startedAt: string | null;
    finishedAt: string | null;
    approvedAt: string | null;
    promotedAt: string | null;
    updatedAt: string | null;
}

export type DevRequestStatus =
    | "QUEUED"
    | "RUNNING"
    | "DEV_READY"
    | "FAILED"
    | "APPROVED"
    | "PROMOTING"
    | "DONE"
    | "REJECTED"
    | "CANCELLED"
    | "SUPERSEDED"
    | "ANSWERED";

export const fetchDevRequests = () => api.get<DevRequest[]>("/dev-requests").then((r) => r.data);

export const fetchDevRequest = (id: number) => api.get<DevRequest>(`/dev-requests/${id}`).then((r) => r.data);

export const createDevRequest = (body: string, title?: string, parentId?: number, repo?: DevRepo) =>
    api
        .post<DevRequest>("/dev-requests", { body, title: title || null, parentId: parentId ?? null, repo: repo ?? null })
        .then((r) => r.data);

/** 대화 상태. OPEN: Claude 프로세스 띄우는 중, LIVE: 대화 중, CLOSED: 종료. undeployed: 개발 서버에 안 올린 커밋 있음 */
export interface DevConversation {
    rootId: number;
    state: "OPEN" | "LIVE" | "CLOSED";
    openedAt: string | null;
    lastActiveAt: string | null;
    closedAt: string | null;
    closeReason: string | null;
    undeployed: boolean;
}

export const fetchDevConversations = () => api.get<DevConversation[]>("/dev-requests/conversations").then((r) => r.data);
export const startDevConversation = (rootId: number) =>
    api.post<DevConversation>(`/dev-requests/conversations/${rootId}/start`).then((r) => r.data);
export const closeDevConversation = (rootId: number) =>
    api.post<DevConversation>(`/dev-requests/conversations/${rootId}/close`).then((r) => r.data);
export const deployDevConversation = (rootId: number) =>
    api.post<DevRequest>(`/dev-requests/conversations/${rootId}/deploy`).then((r) => r.data);

export const cancelDevRequest =(id: number) => api.post<DevRequest>(`/dev-requests/${id}/cancel`).then((r) => r.data);
export const approveDevRequest = (id: number) => api.post<DevRequest>(`/dev-requests/${id}/approve`).then((r) => r.data);
export const rejectDevRequest = (id: number) => api.post<DevRequest>(`/dev-requests/${id}/reject`).then((r) => r.data);

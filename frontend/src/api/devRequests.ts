import api from "@/lib/axios";

/** 서버 DevRequestService.DevRequest 와 1:1. 시간은 "YYYY-MM-DDTHH:mm:ss". */
export interface DevRequest {
    id: number;
    parentId: number | null;
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
    | "SUPERSEDED";

export const fetchDevRequests = () => api.get<DevRequest[]>("/dev-requests").then((r) => r.data);

export const fetchDevRequest = (id: number) => api.get<DevRequest>(`/dev-requests/${id}`).then((r) => r.data);

export const createDevRequest = (body: string, title?: string, parentId?: number) =>
    api.post<DevRequest>("/dev-requests", { body, title: title || null, parentId: parentId ?? null }).then((r) => r.data);

export const cancelDevRequest = (id: number) => api.post<DevRequest>(`/dev-requests/${id}/cancel`).then((r) => r.data);
export const approveDevRequest = (id: number) => api.post<DevRequest>(`/dev-requests/${id}/approve`).then((r) => r.data);
export const rejectDevRequest = (id: number) => api.post<DevRequest>(`/dev-requests/${id}/reject`).then((r) => r.data);

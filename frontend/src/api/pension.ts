// 연금복권 API 클라이언트
// PensionController 대응

export interface PensionRound {
    roundNumber: number;
    drawDate: string;
}

export interface PensionResultResponse {
    roundNumber: number;
    winningJo: number;
    winningNumbers: string;
    bonusNumber: string;
    drawDate: string;
}

export interface PensionStatsResponse {
    totalRounds: number;
    latestRound: number;
    oldestRound: number;
}

export interface PensionTicket {
    id: number;
    round: number | null;
    jo: number;
    n1: number;
    n2: number;
    n3: number;
    n4: number;
    n5: number;
    n6: number;
    source: string;
    purchasedAt: string | null;
    createdAt: string;
}

export interface PensionTicketRequest {
    round: number | null;
    jo: number;
    n1: number;
    n2: number;
    n3: number;
    n4: number;
    n5: number;
    n6: number;
    source: string;
}

export interface PensionTicketResponse {
    success: boolean;
    message: string;
    tickets?: PensionTicket[];
    savedCount: number;
    duplicateCount: number;
}

async function getJson<T>(url: string): Promise<T> {
    const res = await fetch(url);
    if (!res.ok) {
        throw new Error(`요청 실패 (${res.status})`);
    }
    return res.json();
}

export function fetchPensionRounds(): Promise<PensionRound[]> {
    return getJson("/api/pension/rounds");
}

export function fetchPensionLatest(): Promise<PensionResultResponse> {
    return getJson("/api/pension/latest");
}

export function fetchPensionRound(roundNumber: number): Promise<PensionResultResponse> {
    return getJson(`/api/pension/${roundNumber}`);
}

export function fetchPensionStats(): Promise<PensionStatsResponse> {
    return getJson("/api/pension/stats");
}

export function fetchPensionTickets(): Promise<PensionTicket[]> {
    return getJson("/api/pension/ticket");
}

export async function registerPensionTicket(
    round: number,
    jo: number,
    numbers: number[],
    purchasedAt?: string
): Promise<PensionTicketResponse> {
    const res = await fetch("/api/pension/ticket", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ round, jo, numbers, purchasedAt }),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
        throw new Error(body.message ?? "등록에 실패했습니다.");
    }
    return body;
}
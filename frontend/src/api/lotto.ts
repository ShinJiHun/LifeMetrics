// 로또 페이지(통계/기록, 생성) API 클라이언트.
// LottoController / LottoTicketController / LottoAdminController 대응.

export interface LottoRound {
    roundNo: number;
    drawDate: string;
}

export interface LottoRecommendGame {
    gameNo: number;
    num1: number;
    num2: number;
    num3: number;
    num4: number;
    num5: number;
    num6: number;
}

export interface LottoCurrentResponse {
    roundNo: number;
    recommendations: LottoRecommendGame[];
}

export interface LottoRecommendResult {
    gameNo: number;
    matchCount: number;
    bonusMatch: boolean;
}

export interface LottoResultResponse {
    round: number;
    winningNumbers: number[];
    bonus: number;
    results: LottoRecommendResult[];
}

export interface LottoNumberFrequency {
    number: number;
    count: number;
}

export interface LottoStatsResponse {
    totalRounds: number;
    numberFrequency: LottoNumberFrequency[];
    hotNumbers: LottoNumberFrequency[];
    coldNumbers: LottoNumberFrequency[];
    oddEvenDistribution: Record<string, number>;
    lowHighDistribution: Record<string, number>;
    avgSum: number;
    avgConsecutivePairCount: number;
}

export interface LottoTicket {
    id: number;
    ticketGroup: string;
    round: number | null;
    gameNo: number;
    numbers: number[];
    source: string;
    imagePath: string | null;
    purchasedAt: string | null;
    issuedAt: string | null;
    createdAt: string;
    duplicate: boolean;
    oddCount: number;
    evenCount: number;
    lowCount: number;
    highCount: number;
    sum: number;
    consecutivePairCount: number;
    matchCount: number | null;
    bonusMatch: boolean | null;
}

export interface LottoTicketUploadResponse {
    success: boolean;
    message: string;
    tickets: LottoTicket[];
}

export interface LottoSyncResponse {
    fromRound: number;
    toRound: number;
    syncedCount: number;
    message: string;
}

async function getJson<T>(url: string): Promise<T> {
    const res = await fetch(url);
    if (!res.ok) {
        throw new Error(`요청 실패 (${res.status})`);
    }
    return res.json();
}

export function fetchLottoRounds(): Promise<LottoRound[]> {
    return getJson("/api/lotto/round/list");
}

export function fetchLottoCurrent(): Promise<LottoCurrentResponse> {
    return getJson("/api/lotto/round/current");
}

export function fetchLottoRoundResult(round: number): Promise<LottoResultResponse> {
    return getJson(`/api/lotto/round/${round}/result`);
}

export function fetchLottoStats(): Promise<LottoStatsResponse> {
    return getJson("/api/lotto/stats");
}

export function fetchLottoTickets(): Promise<LottoTicket[]> {
    // 최신 50개만 가져옴 (성능 최적화)
    return getJson("/api/lotto/ticket/list?page=0&size=50");
}

export async function uploadLottoTicket(file: File): Promise<LottoTicketUploadResponse> {
    const formData = new FormData();
    formData.append("file", file);

    const res = await fetch("/api/lotto/ticket/upload", { method: "POST", body: formData });
    if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        if (body.code === "ADMIN_REQUIRED") {
            throw new Error("관리자 비밀번호로 로그인하여 시도해주세요.");
        }
        throw new Error(body.message ?? "업로드에 실패했습니다.");
    }
    return res.json();
}

/** 로또 용지 QR코드를 스캔해서 얻은 텍스트(동행복권 확인 URL)로 회차/번호를 등록한다. */
export async function registerLottoTicketByQr(qrText: string): Promise<LottoTicketUploadResponse> {
    const res = await fetch("/api/lotto/ticket/qr", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ qrText }),
    });
    if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        if (body.code === "ADMIN_REQUIRED") {
            throw new Error("관리자 비밀번호로 로그인하여 시도해주세요.");
        }
        throw new Error(body.message ?? "QR 인식에 실패했습니다.");
    }
    return res.json();
}

/** 동행복권 공개 API에서 새 회차 당첨번호를 가져와 채운다 (관리자 전용). */
export async function syncLottoNumbers(): Promise<LottoSyncResponse> {
    const res = await fetch("/api/lotto/admin/sync", { method: "POST" });
    if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        if (body.code === "ADMIN_REQUIRED") {
            throw new Error("관리자 비밀번호로 로그인하여 시도해주세요.");
        }
        throw new Error(body.message ?? "동기화에 실패했습니다.");
    }
    return res.json();
}

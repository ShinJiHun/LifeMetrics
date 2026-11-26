import api from "@/lib/axios";

// 증권 페르소나 — 토스증권 계좌 조회 (관리자 전용, 읽기만). 숫자는 서버 BigDecimal → JSON number.

export interface AccountSnapshot {
    id: number;
    snapshotAt: string;
    snapshotDate: string;
    session: string; // KR_CLOSE / US_CLOSE
    accountSuffix: string;
    purchaseKrw: number | null;
    purchaseUsd: number | null;
    evalKrw: number | null;
    evalUsd: number | null;
    pnlKrw: number | null;
    pnlUsd: number | null;
    pnlAfterCostKrw: number | null;
    pnlAfterCostUsd: number | null;
    pnlRate: number | null;
    pnlRateAfterCost: number | null;
    dailyPnlKrw: number | null;
    dailyPnlUsd: number | null;
    dailyPnlRate: number | null;
    holdingCount: number;
}

export interface Holding {
    market: string;
    symbol: string;
    name: string | null;
    currency: string;
    quantity: number;
    avgPrice: number | null;
    currentPrice: number | null;
    purchaseAmount: number | null;
    evalAmount: number | null;
    pnl: number | null;
    pnlRate: number | null;
    dailyPnl: number | null;
    dailyPnlRate: number | null;
}

export interface StockSummary {
    latest: AccountSnapshot | null;
    holdings: Holding[];
    history: AccountSnapshot[];
}

export interface StockTransaction {
    id: number;
    accountSuffix: string;
    settlementDate: string;
    settled: boolean;
    transactionTime: string | null;
    transactionType: string;
    cancellation: boolean;
    market: string;
    currency: string;
    assetClass: string | null;
    symbol: string | null;
    name: string | null;
    quantity: number | null;
    filledPrice: number | null;
    amount: number | null;
    commission: number | null;
    tax: number | null;
    netAmount: number | null;
    exchangeRate: number | null;
}

export interface TxFilter {
    from?: string;
    to?: string;
    type?: string;
    market?: string;
    symbol?: string;
}

export async function fetchStockSummary(): Promise<StockSummary> {
    const { data } = await api.get<StockSummary>("/stock/summary");
    return data;
}

export async function fetchStockTransactions(f: TxFilter): Promise<StockTransaction[]> {
    const params = Object.fromEntries(Object.entries(f).filter(([, v]) => v));
    const { data } = await api.get<StockTransaction[]>("/stock/transactions", { params });
    return data;
}

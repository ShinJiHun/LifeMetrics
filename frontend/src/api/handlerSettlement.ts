import api from "@/lib/axios";

/** 핸들러 월별 정산서 1개. 서버 HandlerSettlementController.SettlementFile 과 1:1. */
export interface SettlementFile {
    filename: string; // 서버 저장명 "2026-10__원본.pdf"
    month: string; // 귀속월 "YYYY-MM" (PDF 에서 읽음, 실패 시 파일명 기준)
    originalName: string;
    size: number;
    uploadedAt: string;
    parsed: boolean; // PDF 에서 귀속월·지급금액을 읽었는지
    duplicate: boolean; // 같은 월의 더 최근 파일이 있어 합계에서 빠짐
    startDate: string | null;
    endDate: string | null;
    workDays: number | null;
    trips: number | null;
    serviceType: string | null;
    issuedDate: string | null;
    grossAmount: number | null; // 지급금액 (세전으로 간주)
    incomeTax: number | null; // 3%
    localTax: number | null; // 0.3%
    netAmount: number | null; // 실수령 추정
}

const BASE = "/human/handler-settlements";

export async function fetchSettlements(): Promise<SettlementFile[]> {
    const res = await api.get<SettlementFile[]>(BASE);
    return res.data;
}

/** month 는 PDF 에서 귀속월을 못 읽을 때만 쓰인다. */
export async function uploadSettlement(file: File, month?: string): Promise<SettlementFile> {
    const form = new FormData();
    if (month) form.append("month", month);
    form.append("file", file);
    const res = await api.post<SettlementFile>(BASE, form, {
        headers: { "Content-Type": "multipart/form-data" },
    });
    return res.data;
}

export async function deleteSettlement(filename: string): Promise<void> {
    await api.delete(`${BASE}/${encodeURIComponent(filename)}`);
}

/** iframe / 새 탭에서 여는 PDF 주소 (같은 출처라 관리자 쿠키가 실린다). */
export function settlementPdfUrl(filename: string): string {
    return `/api${BASE}/${encodeURIComponent(filename)}`;
}

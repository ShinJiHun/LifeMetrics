import { useEffect, useState } from "react";
import { fetchPensionTickets, type PensionTicket } from "@/api/pension";

const ACCENT = "#7c3aed";

export default function PensionRecordsPage() {
    const [tickets, setTickets] = useState<PensionTicket[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const loadTickets = async () => {
            try {
                setLoading(true);
                const data = await fetchPensionTickets();
                setTickets(data.sort((a, b) => {
                    // 최신순으로 정렬
                    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
                }));
            } catch (err) {
                setError((err as Error).message || "데이터를 불러올 수 없습니다.");
            } finally {
                setLoading(false);
            }
        };
        loadTickets();
    }, []);

    if (loading) {
        return <div style={{ padding: 24 }}>불러오는 중...</div>;
    }

    if (error) {
        return (
            <div style={{ padding: 24 }}>
                <h2>📋 연금복권 기록</h2>
                <div
                    style={{
                        marginTop: 16,
                        padding: 16,
                        background: "#fef2f2",
                        border: "1px solid #fecaca",
                        borderRadius: 8,
                        color: "#991b1b",
                    }}
                >
                    {error}
                </div>
            </div>
        );
    }

    return (
        <div style={{ padding: 24, maxWidth: 960 }}>
            <h2 style={{ margin: 0, marginBottom: 20 }}>📋 연금복권 기록</h2>

            {tickets.length === 0 ? (
                <div
                    style={{
                        padding: 32,
                        textAlign: "center",
                        background: "#f3f4f6",
                        borderRadius: 8,
                        color: "#6b7280",
                    }}
                >
                    <p>등록된 연금복권 기록이 없습니다.</p>
                </div>
            ) : (
                <div>
                    <div style={{ marginBottom: 12, fontSize: 14, color: "#6b7280" }}>
                        총 <strong>{tickets.length}</strong>개 기록
                    </div>

                    {tickets.map((ticket) => (
                        <TicketCard key={ticket.id} ticket={ticket} />
                    ))}
                </div>
            )}
        </div>
    );
}

function TicketCard({ ticket }: { ticket: PensionTicket }) {
    const date = new Date(ticket.createdAt);
    const dateStr = date.toLocaleDateString("ko-KR");
    const timeStr = date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });

    // 번호를 연속으로 표시: 384363
    const numbers = [ticket.n1, ticket.n2, ticket.n3, ticket.n4, ticket.n5, ticket.n6];
    const numberString = numbers.map(n => String(n).padStart(2, "0")).join("");

    return (
        <div
            style={{
                padding: 16,
                marginBottom: 12,
                background: "#fff",
                border: `1px solid #e5e7eb`,
                borderRadius: 8,
                borderLeft: `4px solid ${ACCENT}`,
            }}
        >
            {/* 헤더: 회차, 당첨조, 날짜 */}
            <div
                style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "start",
                    marginBottom: 12,
                }}
            >
                <div>
                    <div style={{ fontSize: 12, color: "#9ca3af", marginBottom: 4 }}>
                        {dateStr} {timeStr}
                    </div>
                    <div style={{ fontSize: 16, fontWeight: 700, color: "#111" }}>
                        {ticket.round}회차 · {ticket.jo}조
                    </div>
                </div>
                <div
                    style={{
                        padding: "4px 12px",
                        background: ACCENT + "20",
                        color: ACCENT,
                        borderRadius: 4,
                        fontSize: 12,
                        fontWeight: 700,
                    }}
                >
                    {ticket.source === "MANUAL" ? "📝 직접 입력" : "🔳 QR 스캔"}
                </div>
            </div>

            {/* 번호 - 큰 폰트로 연속 표시 */}
            <div style={{ marginBottom: 12 }}>
                <div style={{ fontSize: 12, color: ACCENT, marginBottom: 8, fontWeight: 700 }}>
                    구매 번호
                </div>
                <div
                    style={{
                        fontSize: 32,
                        fontWeight: 700,
                        letterSpacing: 2,
                        fontFamily: "monospace",
                        color: ACCENT,
                        padding: "16px 12px",
                        background: ACCENT + "10",
                        borderRadius: 8,
                    }}
                >
                    {numberString}
                </div>
            </div>

            {/* 개별 번호 표시 */}
            <div style={{ marginBottom: 12 }}>
                <div
                    style={{
                        display: "flex",
                        gap: 4,
                        flexWrap: "wrap",
                    }}
                >
                    {numbers.map((num, i) => (
                        <div
                            key={i}
                            style={{
                                width: 40,
                                height: 40,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                background: ACCENT + "20",
                                color: ACCENT,
                                borderRadius: 6,
                                fontSize: 14,
                                fontWeight: 700,
                            }}
                        >
                            {String(num).padStart(2, "0")}
                        </div>
                    ))}
                </div>
            </div>

            {/* 구매일 정보 */}
            {ticket.purchasedAt && (
                <div
                    style={{
                        paddingTop: 12,
                        borderTop: "1px solid #f3f4f6",
                        fontSize: 12,
                        color: "#9ca3af",
                    }}
                >
                    🗓️ 구매일: {new Date(ticket.purchasedAt).toLocaleDateString("ko-KR")}
                </div>
            )}
        </div>
    );
}

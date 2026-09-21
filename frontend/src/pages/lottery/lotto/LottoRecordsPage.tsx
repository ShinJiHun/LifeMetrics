import { useEffect, useState } from "react";
import { fetchLottoTickets, type LottoTicket } from "@/api/lotto";

const ACCENT = "#16a34a";

export default function LottoRecordsPage() {
    const [tickets, setTickets] = useState<LottoTicket[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const loadTickets = async () => {
            try {
                setLoading(true);
                const data = await fetchLottoTickets();
                setTickets(data.sort((a, b) => {
                    // 최신순으로 정렬
                    if (a.round !== b.round && a.round !== null && b.round !== null) {
                        return b.round - a.round;
                    }
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
                <h2>📋 로또 기록</h2>
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
            <h2 style={{ margin: 0, marginBottom: 20 }}>📋 로또 기록</h2>

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
                    <p>등록된 로또 티켓이 없습니다.</p>
                </div>
            ) : (
                <div>
                    <div style={{ marginBottom: 12, fontSize: 14, color: "#6b7280" }}>
                        총 <strong>{tickets.length}</strong>개 티켓
                    </div>

                    {tickets.map((ticket) => (
                        <TicketCard key={ticket.id} ticket={ticket} accent={ACCENT} />
                    ))}
                </div>
            )}
        </div>
    );
}

function TicketCard({ ticket, accent }: { ticket: LottoTicket; accent: string }) {
    const date = new Date(ticket.createdAt);
    const dateStr = date.toLocaleDateString("ko-KR");
    const timeStr = date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });

    const matchStatus =
        ticket.matchCount === null
            ? "결과 확인 중"
            : ticket.matchCount === 0
              ? "당첨 안 됨"
              : `${ticket.matchCount}개 번호 일치${ticket.bonusMatch ? " + 보너스" : ""}`;

    const matchColor =
        ticket.matchCount === null ? "#9ca3af" : ticket.matchCount === 0 ? "#6b7280" : ticket.matchCount >= 4 ? "#dc2626" : "#f59e0b";

    return (
        <div
            style={{
                padding: 16,
                marginBottom: 12,
                background: "#fff",
                border: `1px solid #e5e7eb`,
                borderRadius: 8,
                borderLeft: `4px solid ${accent}`,
            }}
        >
            {/* 헤더: 회차, 날짜, 게임번호 */}
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
                        {ticket.round !== null ? `${ticket.round}회차` : "회차 미확인"} • 게임{ticket.gameNo}
                    </div>
                </div>
                <div
                    style={{
                        padding: "4px 12px",
                        background: matchColor + "20",
                        color: matchColor,
                        borderRadius: 4,
                        fontSize: 12,
                        fontWeight: 700,
                    }}
                >
                    {matchStatus}
                </div>
            </div>

            {/* 번호 */}
            <div style={{ marginBottom: 12 }}>
                <div style={{ fontSize: 12, color: accent, marginBottom: 6, fontWeight: 700 }}>
                    구매 번호
                </div>
                <div
                    style={{
                        display: "flex",
                        gap: 6,
                        flexWrap: "wrap",
                    }}
                >
                    {ticket.numbers.map((num, i) => (
                        <div
                            key={i}
                            style={{
                                width: 32,
                                height: 32,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                background: accent + "20",
                                color: accent,
                                borderRadius: 4,
                                fontSize: 12,
                                fontWeight: 700,
                            }}
                        >
                            {num}
                        </div>
                    ))}
                </div>
            </div>

            {/* 분석 정보 */}
            <div
                style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))",
                    gap: 12,
                    paddingTop: 12,
                    borderTop: "1px solid #f3f4f6",
                }}
            >
                <div>
                    <div style={{ fontSize: 11, color: "#9ca3af", marginBottom: 2 }}>홀짝</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: "#111" }}>
                        홀 {ticket.oddCount}개 / 짝 {ticket.evenCount}개
                    </div>
                </div>
                <div>
                    <div style={{ fontSize: 11, color: "#9ca3af", marginBottom: 2 }}>고저</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: "#111" }}>
                        저 {ticket.lowCount}개 / 고 {ticket.highCount}개
                    </div>
                </div>
                <div>
                    <div style={{ fontSize: 11, color: "#9ca3af", marginBottom: 2 }}>합계</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: "#111" }}>
                        {ticket.sum}
                    </div>
                </div>
                <div>
                    <div style={{ fontSize: 11, color: "#9ca3af", marginBottom: 2 }}>연속쌍</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: "#111" }}>
                        {ticket.consecutivePairCount}개
                    </div>
                </div>
            </div>

            {ticket.duplicate && (
                <div
                    style={{
                        marginTop: 12,
                        padding: 8,
                        background: "#fef3c7",
                        borderRadius: 4,
                        fontSize: 12,
                        color: "#92400e",
                    }}
                >
                    ⚠️ 중복된 티켓입니다 (같은 회차, 같은 게임, 같은 번호)
                </div>
            )}
        </div>
    );
}

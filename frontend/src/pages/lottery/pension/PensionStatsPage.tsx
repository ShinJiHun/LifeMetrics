import { useEffect, useMemo, useState } from "react";

import {
    fetchPensionRounds,
    fetchPensionRound,
    fetchPensionStats,
    fetchPensionTickets,
    type PensionRound,
    type PensionResultResponse,
    type PensionStatsResponse,
    type PensionTicket,
} from "@/api/pension";

const PENSION_COLOR = "#7c3aed";

export default function PensionStatsPage() {
    // 연금복권 상태
    const [rounds, setRounds] = useState<PensionRound[]>([]);
    const [selectedRound, setSelectedRound] = useState<number | null>(null);
    const [result, setResult] = useState<PensionResultResponse | null>(null);
    const [stats, setStats] = useState<PensionStatsResponse | null>(null);
    const [tickets, setTickets] = useState<PensionTicket[]>([]);

    // 공통 상태
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const loadPensionData = async () => {
        try {
            const [roundList, statsData, ticketList] = await Promise.all([
                fetchPensionRounds(),
                fetchPensionStats(),
                fetchPensionTickets(),
            ]);
            setRounds(roundList);
            setStats(statsData);
            setTickets(ticketList);
            if (roundList.length > 0) {
                setSelectedRound(roundList[0].roundNumber);
            }
        } catch (e) {
            throw new Error(
                (e as Error).message || "연금복권 데이터를 불러오지 못했습니다."
            );
        }
    };

    const loadBase = async () => {
        setLoading(true);
        setError(null);
        try {
            await loadPensionData();
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadBase();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        if (selectedRound == null) return;
        fetchPensionRound(selectedRound)
            .then(setResult)
            .catch(() => setResult(null));
    }, [selectedRound]);

    const ticketsByRound = useMemo(() => {
        const map = new Map<string, PensionTicket[]>();
        for (const t of tickets) {
            const key = t.round != null ? String(t.round) : "미확인";
            if (!map.has(key)) map.set(key, []);
            map.get(key)!.push(t);
        }
        return Array.from(map.entries());
    }, [tickets]);

    if (loading) return <div style={{ padding: 24 }}>불러오는 중...</div>;

    if (error) {
        return (
            <div style={{ padding: 24 }}>
                <h2>🎫 로또 연금복권 통계·기록</h2>
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
            <h2 style={{ margin: 0, marginBottom: 20 }}>🎫 로또 연금복권 통계·기록</h2>

            <div>
                {/* 회차 선택 + 당첨번호 */}
                <section style={{ marginBottom: 20 }}>
                    <div
                        style={{
                            display: "flex",
                            alignItems: "center",
                            gap: 10,
                            marginBottom: 12,
                            flexWrap: "wrap",
                        }}
                    >
                        <select
                            value={selectedRound ?? ""}
                            onChange={(e) => setSelectedRound(Number(e.target.value))}
                            style={{
                                padding: "6px 10px",
                                borderRadius: 6,
                                border: "1px solid #e5e7eb",
                            }}
                        >
                            {rounds.map((r) => (
                                <option key={r.roundNumber} value={r.roundNumber}>
                                    {r.roundNumber}회 ({r.drawDate})
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* 회차 리스트 */}
                    {rounds.length > 0 && (
                        <div
                            style={{
                                display: "flex",
                                gap: 6,
                                flexWrap: "wrap",
                                marginBottom: 12,
                            }}
                        >
                            {rounds.slice(0, 10).map((r) => (
                                <button
                                    key={r.roundNumber}
                                    onClick={() => setSelectedRound(r.roundNumber)}
                                    style={{
                                        padding: "6px 12px",
                                        borderRadius: 20,
                                        border:
                                            selectedRound === r.roundNumber
                                                ? `2px solid ${PENSION_COLOR}`
                                                : "1px solid #e5e7eb",
                                        background:
                                            selectedRound === r.roundNumber
                                                ? "#f5f3ff"
                                                : "#fff",
                                        color:
                                            selectedRound === r.roundNumber
                                                ? PENSION_COLOR
                                                : "#6b7280",
                                        fontWeight:
                                            selectedRound === r.roundNumber
                                                ? 600
                                                : 500,
                                        fontSize: 12,
                                        cursor: "pointer",
                                        transition:
                                            "all 0.2s ease",
                                    }}
                                >
                                    {r.roundNumber}회
                                </button>
                            ))}
                        </div>
                    )}

                    {result && (
                        <section
                            style={{
                                padding: 20,
                                background: "#f5f3ff",
                                borderRadius: 12,
                                border: `1px solid ${PENSION_COLOR}40`,
                            }}
                        >
                            <div style={{ marginBottom: 12 }}>
                                <div style={{ fontSize: 12, color: PENSION_COLOR }}>
                                    당첨 결과
                                </div>
                                <div style={{ fontSize: 28, fontWeight: 700, color: "#000" }}>
                                    {result.roundNumber}회차
                                </div>
                                <div
                                    style={{
                                        fontSize: 14,
                                        color: "#666",
                                        marginTop: 4,
                                    }}
                                >
                                    {result.drawDate}
                                </div>
                            </div>

                            <div
                                style={{
                                    display: "grid",
                                    gridTemplateColumns: "1fr 1fr",
                                    gap: 16,
                                    marginTop: 16,
                                }}
                            >
                                <div>
                                    <div
                                        style={{
                                            fontSize: 12,
                                            color: PENSION_COLOR,
                                            marginBottom: 8,
                                        }}
                                    >
                                        1등 번호
                                    </div>
                                    <div
                                        style={{
                                            fontSize: 24,
                                            fontWeight: 700,
                                            letterSpacing: 2,
                                            fontFamily: "monospace",
                                        }}
                                    >
                                        {result.winningNumbers.split("").join(" ")}
                                    </div>
                                </div>

                                <div>
                                    <div
                                        style={{
                                            fontSize: 12,
                                            color: PENSION_COLOR,
                                            marginBottom: 8,
                                        }}
                                    >
                                        당첨조
                                    </div>
                                    <div
                                        style={{
                                            fontSize: 28,
                                            fontWeight: 700,
                                            color: "#dc2626",
                                        }}
                                    >
                                        {result.winningJo}조
                                    </div>
                                </div>
                            </div>

                            <div style={{ marginTop: 16 }}>
                                <div
                                    style={{
                                        fontSize: 12,
                                        color: PENSION_COLOR,
                                        marginBottom: 8,
                                    }}
                                >
                                    보너스 번호
                                </div>
                                <div
                                    style={{
                                        padding: 12,
                                        background: "#fef3c7",
                                        borderRadius: 8,
                                        fontSize: 20,
                                        fontWeight: 700,
                                        letterSpacing: 2,
                                        fontFamily: "monospace",
                                    }}
                                >
                                    {result.bonusNumber.split("").join(" ")}
                                </div>
                            </div>
                        </section>
                    )}
                </section>

                {/* 전체 통계 */}
                {stats && (
                    <section style={{ marginBottom: 36 }}>
                        <h3>📊 전체 통계</h3>

                        <div
                            style={{
                                display: "flex",
                                gap: 24,
                                flexWrap: "wrap",
                                margin: "12px 0",
                            }}
                        >
                            <StatCard
                                label="총 회차 수"
                                value={String(stats.totalRounds)}
                            />
                            <StatCard
                                label="최신 회차"
                                value={`${stats.latestRound}회차`}
                            />
                            <StatCard
                                label="가장 오래된 회차"
                                value={`${stats.oldestRound}회차`}
                            />
                        </div>
                    </section>
                )}

                <div
                    style={{
                        marginTop: 24,
                        padding: 16,
                        background: "#f0fdf4",
                        borderRadius: 8,
                        border: "1px solid #bbf7d0",
                        color: "#166534",
                        fontSize: 14,
                    }}
                >
                    💡 연금복권은 매주 목요일에 방송됩니다. 최신 데이터는 자동으로
                    동기화됩니다.
                </div>

                {/* 내가 산 연금복권 기록 */}
                <section style={{ marginTop: 36 }}>
                    <h3>🧾 내 연금복권 기록 ({tickets.length}장)</h3>
                    {ticketsByRound.length === 0 ? (
                        <div
                            style={{
                                color: "#9aa0b2",
                                fontSize: 14,
                                marginTop: 8,
                            }}
                        >
                            아직 저장된 티켓이 없어요. 위의 "생성" 메뉴에서 번호를 입력해보세요.
                        </div>
                    ) : (
                        ticketsByRound.map(([round, games]) => (
                            <div key={round} style={{ marginTop: 16 }}>
                                <div
                                    style={{
                                        fontWeight: 600,
                                        marginBottom: 8,
                                        color: "#374151",
                                    }}
                                >
                                    {round === "미확인" ? "회차 미확인" : `${round}회`}
                                </div>
                                <div
                                    style={{
                                        display: "flex",
                                        flexDirection: "column",
                                        gap: 8,
                                    }}
                                >
                                    {games.map((t) => {
                                        const numberString = [
                                            t.n1,
                                            t.n2,
                                            t.n3,
                                            t.n4,
                                            t.n5,
                                            t.n6,
                                        ]
                                            .map((n) =>
                                                String(n).padStart(2, "0")
                                            )
                                            .join("");

                                        return (
                                            <div
                                                key={t.id}
                                                style={{
                                                    display: "flex",
                                                    alignItems: "center",
                                                    gap: 10,
                                                    padding: "10px 14px",
                                                    background: "#f9fafb",
                                                    borderRadius: 10,
                                                    flexWrap: "wrap",
                                                }}
                                            >
                                                <div
                                                    style={{
                                                        fontSize: 16,
                                                        fontWeight: 700,
                                                        fontFamily: "monospace",
                                                        color: PENSION_COLOR,
                                                    }}
                                                >
                                                    {numberString}
                                                </div>
                                                <span
                                                    style={{
                                                        fontSize: 12,
                                                        color: "#6b7280",
                                                    }}
                                                >
                                                    {t.jo}조
                                                </span>
                                                <span
                                                    style={{
                                                        fontSize: 12,
                                                        color: "#9ca3af",
                                                    }}
                                                >
                                                    {t.source}
                                                </span>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        ))
                    )}
                </section>
            </div>
        </div>
    );
}

function StatCard({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <div style={{ fontSize: 12, color: "#9aa0b2" }}>{label}</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: "#111" }}>
                {value}
            </div>
        </div>
    );
}

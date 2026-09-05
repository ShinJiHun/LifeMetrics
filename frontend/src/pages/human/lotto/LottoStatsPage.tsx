import { useEffect, useMemo, useState } from "react";
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
} from "recharts";

import {
    fetchLottoRounds,
    fetchLottoRoundResult,
    fetchLottoStats,
    fetchLottoTickets,
    syncLottoNumbers,
    type LottoRound,
    type LottoResultResponse,
    type LottoStatsResponse,
    type LottoTicket,
} from "@/api/lotto";
import LottoNumberBall from "./LottoNumberBall";

const ACCENT = "#16a34a";

export default function LottoStatsPage() {
    const [rounds, setRounds] = useState<LottoRound[]>([]);
    const [selectedRound, setSelectedRound] = useState<number | null>(null);
    const [result, setResult] = useState<LottoResultResponse | null>(null);
    const [stats, setStats] = useState<LottoStatsResponse | null>(null);
    const [tickets, setTickets] = useState<LottoTicket[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [syncing, setSyncing] = useState(false);
    const [syncMessage, setSyncMessage] = useState<string | null>(null);

    const loadBase = async () => {
        setLoading(true);
        setError(null);
        try {
            const [roundList, statsData, ticketList] = await Promise.all([
                fetchLottoRounds(),
                fetchLottoStats(),
                fetchLottoTickets(),
            ]);
            setRounds(roundList);
            setStats(statsData);
            setTickets(ticketList);
            if (roundList.length > 0) {
                setSelectedRound(roundList[0].roundNo);
            }
        } catch (e) {
            setError(
                (e as Error).message ||
                    "로또 데이터를 불러오지 못했습니다. lotto 데이터소스가 아직 꺼져 있을 수 있어요."
            );
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
        fetchLottoRoundResult(selectedRound)
            .then(setResult)
            .catch(() => setResult(null));
    }, [selectedRound]);

    const handleSync = async () => {
        setSyncing(true);
        setSyncMessage(null);
        try {
            const res = await syncLottoNumbers();
            setSyncMessage(res.message);
            await loadBase();
        } catch (e) {
            setSyncMessage((e as Error).message);
        } finally {
            setSyncing(false);
        }
    };

    const oddEvenChartData = useMemo(() => {
        if (!stats) return [];
        return Object.entries(stats.oddEvenDistribution)
            .map(([label, count]) => ({ label, count }))
            .sort((a, b) => a.label.localeCompare(b.label));
    }, [stats]);

    const lowHighChartData = useMemo(() => {
        if (!stats) return [];
        return Object.entries(stats.lowHighDistribution)
            .map(([label, count]) => ({ label, count }))
            .sort((a, b) => a.label.localeCompare(b.label));
    }, [stats]);

    const ticketsByRound = useMemo(() => {
        const map = new Map<string, LottoTicket[]>();
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
                <h2>🎱 로또 통계·기록</h2>
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
            <div
                style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    flexWrap: "wrap",
                    gap: 12,
                }}
            >
                <h2 style={{ margin: 0 }}>🎱 로또 통계·기록</h2>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    {syncMessage && (
                        <span style={{ fontSize: 13, color: "#6b7280" }}>{syncMessage}</span>
                    )}
                    <button
                        onClick={handleSync}
                        disabled={syncing}
                        style={{
                            padding: "8px 14px",
                            borderRadius: 8,
                            border: `1px solid ${ACCENT}`,
                            background: syncing ? "#f3f4f6" : "#fff",
                            color: ACCENT,
                            fontWeight: 600,
                            cursor: syncing ? "default" : "pointer",
                        }}
                    >
                        {syncing ? "동기화 중..." : "🔄 최신 회차 동기화"}
                    </button>
                </div>
            </div>

            {/* 회차 선택 + 당첨번호 */}
            <section style={{ marginTop: 20 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
                    <select
                        value={selectedRound ?? ""}
                        onChange={(e) => setSelectedRound(Number(e.target.value))}
                        style={{ padding: "6px 10px", borderRadius: 6, border: "1px solid #e5e7eb" }}
                    >
                        {rounds.map((r) => (
                            <option key={r.roundNo} value={r.roundNo}>
                                {r.roundNo}회 ({r.drawDate})
                            </option>
                        ))}
                    </select>
                </div>

                {result && (
                    <div
                        style={{
                            display: "flex",
                            alignItems: "center",
                            gap: 10,
                            padding: 16,
                            background: "#f9fafb",
                            borderRadius: 12,
                            flexWrap: "wrap",
                        }}
                    >
                        {result.winningNumbers.map((n) => (
                            <LottoNumberBall key={n} n={n} />
                        ))}
                        <span style={{ fontSize: 20, color: "#9ca3af" }}>+</span>
                        <LottoNumberBall n={result.bonus} dim />
                    </div>
                )}
            </section>

            {/* 전체 통계 */}
            {stats && (
                <section style={{ marginTop: 32 }}>
                    <h3>📊 전체 통계 (총 {stats.totalRounds}회차)</h3>

                    <div style={{ display: "flex", gap: 24, flexWrap: "wrap", margin: "12px 0" }}>
                        <StatCard label="평균 합계" value={stats.avgSum.toFixed(1)} />
                        <StatCard
                            label="평균 연속번호 쌍"
                            value={stats.avgConsecutivePairCount.toFixed(2)}
                        />
                        <div>
                            <div style={{ fontSize: 12, color: "#9aa0b2", marginBottom: 6 }}>
                                🔥 자주 나온 번호
                            </div>
                            <div style={{ display: "flex", gap: 4 }}>
                                {stats.hotNumbers.slice(0, 6).map((f) => (
                                    <LottoNumberBall key={f.number} n={f.number} size={26} />
                                ))}
                            </div>
                        </div>
                        <div>
                            <div style={{ fontSize: 12, color: "#9aa0b2", marginBottom: 6 }}>
                                🧊 뜸한 번호
                            </div>
                            <div style={{ display: "flex", gap: 4 }}>
                                {stats.coldNumbers.slice(0, 6).map((f) => (
                                    <LottoNumberBall key={f.number} n={f.number} size={26} dim />
                                ))}
                            </div>
                        </div>
                    </div>

                    <div style={{ marginTop: 20 }}>
                        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>
                            번호별 출현 빈도 (1~45)
                        </div>
                        <ResponsiveContainer width="100%" height={220}>
                            <BarChart data={stats.numberFrequency}>
                                <CartesianGrid strokeDasharray="3 3" stroke="#f1f2f4" vertical={false} />
                                <XAxis
                                    dataKey="number"
                                    tick={{ fontSize: 10, fill: "#9aa0b2" }}
                                    interval={2}
                                    axisLine={{ stroke: "#e5e7eb" }}
                                    tickLine={false}
                                />
                                <YAxis
                                    tick={{ fontSize: 11, fill: "#9aa0b2" }}
                                    axisLine={false}
                                    tickLine={false}
                                    width={28}
                                />
                                <Tooltip
                                    formatter={(v: any) => [`${v}회`, "출현"]}
                                    labelFormatter={(l) => `${l}번`}
                                />
                                <Bar dataKey="count" fill={ACCENT} radius={[3, 3, 0, 0]} maxBarSize={14} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>

                    <div style={{ display: "flex", gap: 32, marginTop: 20, flexWrap: "wrap" }}>
                        <div style={{ flex: "1 1 300px", minWidth: 260 }}>
                            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>
                                홀:짝 비율 분포
                            </div>
                            <ResponsiveContainer width="100%" height={180}>
                                <BarChart data={oddEvenChartData}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f2f4" vertical={false} />
                                    <XAxis
                                        dataKey="label"
                                        tick={{ fontSize: 11, fill: "#9aa0b2" }}
                                        axisLine={{ stroke: "#e5e7eb" }}
                                        tickLine={false}
                                    />
                                    <YAxis tick={{ fontSize: 11, fill: "#9aa0b2" }} axisLine={false} tickLine={false} width={28} />
                                    <Tooltip formatter={(v: any) => [`${v}회`, "회차 수"]} />
                                    <Bar dataKey="count" fill="#6366f1" radius={[3, 3, 0, 0]} maxBarSize={28} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>

                        <div style={{ flex: "1 1 300px", minWidth: 260 }}>
                            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>
                                저(1~22):고(23~45) 비율 분포
                            </div>
                            <ResponsiveContainer width="100%" height={180}>
                                <BarChart data={lowHighChartData}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f2f4" vertical={false} />
                                    <XAxis
                                        dataKey="label"
                                        tick={{ fontSize: 11, fill: "#9aa0b2" }}
                                        axisLine={{ stroke: "#e5e7eb" }}
                                        tickLine={false}
                                    />
                                    <YAxis tick={{ fontSize: 11, fill: "#9aa0b2" }} axisLine={false} tickLine={false} width={28} />
                                    <Tooltip formatter={(v: any) => [`${v}회`, "회차 수"]} />
                                    <Bar dataKey="count" fill="#f59e0b" radius={[3, 3, 0, 0]} maxBarSize={28} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </div>
                </section>
            )}

            {/* 내가 산 로또 기록 */}
            <section style={{ marginTop: 36 }}>
                <h3>🧾 내 로또 기록 ({tickets.length}게임)</h3>
                {ticketsByRound.length === 0 ? (
                    <div style={{ color: "#9aa0b2", fontSize: 14, marginTop: 8 }}>
                        아직 저장된 티켓이 없어요. "생성" 메뉴에서 로또 용지 사진을 업로드해보세요.
                    </div>
                ) : (
                    ticketsByRound.map(([round, games]) => (
                        <div key={round} style={{ marginTop: 16 }}>
                            <div style={{ fontWeight: 600, marginBottom: 8, color: "#374151" }}>
                                {round === "미확인" ? "회차 미확인" : `${round}회`}
                            </div>
                            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                                {games.map((t) => (
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
                                        <div style={{ display: "flex", gap: 4 }}>
                                            {t.numbers.map((n) => (
                                                <LottoNumberBall key={n} n={n} size={24} />
                                            ))}
                                        </div>
                                        <span style={{ fontSize: 12, color: "#6b7280" }}>
                                            홀:짝 {t.oddCount}:{t.evenCount} · 저:고 {t.lowCount}:{t.highCount} · 합
                                            {t.sum}
                                        </span>
                                        {t.matchCount != null && (
                                            <span
                                                style={{
                                                    marginLeft: "auto",
                                                    fontSize: 12,
                                                    fontWeight: 700,
                                                    padding: "3px 10px",
                                                    borderRadius: 20,
                                                    background: t.matchCount >= 3 ? "#dcfce7" : "#f3f4f6",
                                                    color: t.matchCount >= 3 ? "#166534" : "#6b7280",
                                                }}
                                            >
                                                {t.matchCount}개 일치{t.bonusMatch ? " +보너스" : ""}
                                            </span>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </div>
                    ))
                )}
            </section>
        </div>
    );
}

function StatCard({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <div style={{ fontSize: 12, color: "#9aa0b2" }}>{label}</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: "#111" }}>{value}</div>
        </div>
    );
}

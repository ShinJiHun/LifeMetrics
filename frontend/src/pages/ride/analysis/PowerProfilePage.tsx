import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
    CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { fetchPowerProfile, type LongRide, type PowerProfile } from "@/api/power";

import "@/styles/global.css";
import "@/styles/power-profile.css";

const TYPE_ICON: Record<string, string> = {
    sprinter: "⚡", puncheur: "👊", pursuiter: "⛰️", tt: "🛣️", allrounder: "🔄", unknown: "❔",
};

const ZONE_COLOR = ["#cbd5e1", "#60a5fa", "#34d399", "#facc15", "#fb923c", "#f87171", "#a855f7"];

export default function PowerProfilePage() {
    const [data, setData] = useState<PowerProfile | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [rideId, setRideId] = useState<number | null>(null);

    useEffect(() => {
        fetchPowerProfile().then((p) => {
            setData(p);
            setRideId(p.durability.longRides[0]?.activityId ?? null);
        }).catch((e: Error) => setError(e.message));
    }, []);

    const ride: LongRide | undefined = useMemo(
        () => data?.durability.longRides.find((r) => r.activityId === rideId),
        [data, rideId],
    );

    if (error) return <div className="pp-page"><div className="pp-state">{error}</div></div>;
    if (!data) return <div className="pp-page"><div className="pp-state">파워 기록 계산 중… (처음엔 몇 초 걸려요)</div></div>;
    if (data.rideCount === 0) return <div className="pp-page"><div className="pp-state">파워미터 기록이 없습니다.</div></div>;

    const t = data.riderType;
    const d = data.durability;
    const curve = data.curve.map((c) => ({ ...c, recent: c.recentWatts || null }));

    return (
        <div className="pp-page">
            <h1>파워 프로필</h1>
            <div className="pp-subtitle">
                파워미터 기록 {data.rideCount}회 · {data.totalHours.toLocaleString()}시간 · {data.totalKm.toLocaleString()}km
                ({data.meterSince}부터) · 체중 {data.weightKg}kg 기준
            </div>

            {/* 성향 */}
            <section className="pp-hero">
                <div className="pp-hero-type">
                    <span className="pp-hero-icon">{TYPE_ICON[t.type] ?? "❔"}</span>
                    <div>
                        <div className="pp-hero-label">라이더 성향</div>
                        <div className="pp-hero-name">{t.label}</div>
                        <div className="pp-hero-desc">{t.description}</div>
                    </div>
                </div>
                <div className="pp-scores">
                    {t.scores.map((s) => (
                        <div key={s.key} className="pp-score">
                            <div className="pp-score-head">
                                <span>{s.label}</span>
                                <span className="pp-score-val">{s.wkg} W/kg · <b>{s.score}</b>점</span>
                            </div>
                            <div className="pp-bar"><div style={{ width: `${s.score}%` }} /></div>
                        </div>
                    ))}
                </div>
            </section>

            <div className="pp-grid">
                <Card title="추정 FTP (20분×0.95)" value={`${data.estimatedFtp}`} unit="W"
                      sub={`${(data.estimatedFtp / data.weightKg).toFixed(2)} W/kg`} />
                <Card title="최근 90일 추정 FTP" value={`${data.recentEstimatedFtp || "-"}`} unit="W" />
                <Card title={data.ftpSettingEstimated ? "설정 FTP (추정값)" : "설정 FTP"} value={`${data.ftpSetting}`} unit="W"
                      sub={diffText(data.estimatedFtp, data.ftpSetting)} />
                <Card title="후반 5분 파워 유지" value={d.retention5mPct == null ? "-" : `${d.retention5mPct}`} unit="%"
                      sub="2000kJ 이후 최고 / 전체 최고" />
            </div>

            {/* 파워 커브 */}
            <div className="pp-section-title">파워 커브 — 시간별 최고 평균 파워</div>
            <div className="pp-chart">
                <ResponsiveContainer width="100%" height={300}>
                    <LineChart data={curve} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                        <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                        <YAxis tick={{ fontSize: 11 }} unit="W" width={56} />
                        <Tooltip formatter={(v, n) => [`${v}W`, n]} />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                        <Line name="전체" type="monotone" dataKey="watts" stroke="#2563eb" strokeWidth={2.5} dot={{ r: 3 }} />
                        <Line name="최근 90일" type="monotone" dataKey="recent" stroke="#f97316" strokeWidth={2}
                              strokeDasharray="5 4" dot={{ r: 2 }} connectNulls />
                    </LineChart>
                </ResponsiveContainer>
            </div>
            <div className="pp-table-wrap">
                <table className="pp-table">
                    <thead>
                    <tr><th>시간</th><th>최고</th><th>W/kg</th><th>최근 90일</th><th>기록한 라이딩</th></tr>
                    </thead>
                    <tbody>
                    {data.curve.map((c) => (
                        <tr key={c.seconds}>
                            <td>{c.label}</td>
                            <td><b>{c.watts}</b>W</td>
                            <td>{c.wkg}</td>
                            <td>{c.recentWatts ? `${c.recentWatts}W` : "-"}</td>
                            <td className="pp-ride-cell">
                                {c.activityId ? <Link to={`/records/riding/${c.activityId}`}>{c.date} {c.name ?? ""}</Link> : "-"}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            {/* 존 분포 */}
            <div className="pp-section-title">파워 존 시간 분포 — 설정 FTP {data.ftpSetting}W 기준, 코스팅 포함</div>
            <div className="pp-zonebar">
                {data.zones.map((z, i) => z.pct > 0 && (
                    <div key={z.zone} style={{ width: `${z.pct}%`, background: ZONE_COLOR[i] }} title={`${z.zone} ${z.pct}%`} />
                ))}
            </div>
            <div className="pp-zones">
                {data.zones.map((z, i) => (
                    <div key={z.zone} className="pp-zone">
                        <span className="pp-dot" style={{ background: ZONE_COLOR[i] }} />
                        <b>{z.zone}</b> {z.label}
                        <span className="pp-muted"> {z.fromW}{z.toW == null ? "W+" : `–${z.toW}W`}</span>
                        <span className="pp-zone-pct">{z.pct}% · {hours(z.seconds)}</span>
                    </div>
                ))}
            </div>

            {/* 피로 저항 */}
            <div className="pp-section-title">피로 저항 — 이미 한 일(kJ)이 쌓인 뒤에도 낼 수 있는 파워</div>
            <div className="pp-table-wrap">
                <table className="pp-table">
                    <thead><tr><th>누적 일</th><th>최고 5분</th><th>최고 20분</th></tr></thead>
                    <tbody>
                    {d.bands.map((b) => (
                        <tr key={b.fromKj}>
                            <td>{b.label}</td>
                            <td>{b.best5m ? `${b.best5m}W` : "-"}</td>
                            <td>{b.best20m ? `${b.best20m}W` : "-"}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            {d.longRides.length > 0 && (
                <>
                    <div className="pp-section-title">
                        장거리 시간별 파워·심박 (4시간 이상 {d.longRides.length}회
                        {d.avgFadePct != null && `, 후반 파워 평균 ${signed(d.avgFadePct)}%`})
                    </div>
                    <select className="pp-select" value={rideId ?? ""} onChange={(e) => setRideId(Number(e.target.value))}>
                        {d.longRides.map((r) => (
                            <option key={r.activityId} value={r.activityId}>
                                {r.date} {r.name ?? ""} ({r.km}km)
                            </option>
                        ))}
                    </select>
                    {ride && (
                        <>
                            <div className="pp-ride-meta">
                                이동 {ride.hours}시간 · NP {ride.np}W · {ride.kj.toLocaleString()}kJ ·
                                후반 파워 {ride.fadePct == null ? "-" : `${signed(ride.fadePct)}%`}
                                {ride.hrDriftPct != null && ` · 심박 드리프트 ${signed(ride.hrDriftPct)}%`}
                            </div>
                            <div className="pp-chart">
                                <ResponsiveContainer width="100%" height={260}>
                                    <LineChart data={ride.hourly} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                                        <XAxis dataKey="hour" tick={{ fontSize: 11 }} unit="h" />
                                        <YAxis yAxisId="p" tick={{ fontSize: 11 }} unit="W" width={52} />
                                        <YAxis yAxisId="h" orientation="right" tick={{ fontSize: 11 }} width={40} domain={["dataMin - 10", "dataMax + 10"]} />
                                        <Tooltip />
                                        <Legend wrapperStyle={{ fontSize: 12 }} />
                                        <Line yAxisId="p" name="평균 파워(W)" dataKey="power" stroke="#2563eb" strokeWidth={2.5} dot={{ r: 3 }} />
                                        <Line yAxisId="h" name="평균 심박(bpm)" dataKey="hr" stroke="#ef4444" strokeWidth={2} dot={{ r: 2 }} connectNulls />
                                    </LineChart>
                                </ResponsiveContainer>
                            </div>
                        </>
                    )}
                </>
            )}

            <div className="pp-notes">
                읽을 때 주의할 점
                <ul>
                    <li>파워 커브는 실제로 낸 최고값이라, <b>전력 테스트(스프린트·5분·20분 올아웃)를 한 적이 없으면 짧은 구간이 낮게</b> 나오고 성향이 지구력 쪽으로 치우칩니다.</li>
                    <li>성향 점수는 Coggan 파워 프로필 표(남성)의 최하단~최상단을 0~100으로 근사한 상대값입니다. 등급 판정용이 아니라 어느 쪽이 상대적으로 강한지 보는 용도예요.</li>
                    <li>{data.meterSince} 이전 라이딩의 파워는 파워미터 없이 앱이 추정한 값이라 뺐습니다.</li>
                    <li>심박 드리프트: 처음 대비 마지막 1/4 구간에서 같은 파워에 심박이 얼마나 더 올랐는지(+면 피로·탈수 신호).</li>
                </ul>
            </div>
        </div>
    );
}

function Card({ title, value, unit, sub }: { title: string; value: string; unit: string; sub?: string }) {
    return (
        <div className="pp-card">
            <div className="pp-card-title">{title}</div>
            <div className="pp-card-value">{value}<span className="pp-card-unit">{unit}</span></div>
            {sub && <div className="pp-card-sub">{sub}</div>}
        </div>
    );
}

function signed(v: number) {
    return v > 0 ? `+${v}` : `${v}`;
}

function hours(sec: number) {
    const h = sec / 3600;
    return h >= 1 ? `${h.toFixed(1)}시간` : `${Math.round(sec / 60)}분`;
}

function diffText(est: number, setting: number) {
    if (!est || !setting) return undefined;
    const diff = est - setting;
    if (Math.abs(diff) < 5) return "추정값과 비슷해요";
    return diff > 0 ? `추정 FTP가 ${diff}W 높아요` : `추정 FTP가 ${-diff}W 낮아요`;
}

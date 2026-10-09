// 라이딩 상세 분석: 요약 지표, 속도/파워/케이던스/심박/고도 그래프, 파워존·심박존, 파워커브, 인터벌
//
// 그래프는 지표마다 축 하나짜리 작은 차트를 세로로 쌓고(syncId 로 커서 동기화),
// 서로 다른 단위를 한 차트에 이중 축으로 겹치지 않는다.

import {useEffect, useState} from "react";
import type {CSSProperties} from "react";
import {
    Area, AreaChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import AdminOnly from "@/components/common/AdminOnly";
import {fetchActivityAnalytics, saveZoneSetting} from "@/api/analytics";
import type {ActivityAnalytics, StreamPoint, ZoneTime} from "@/api/analytics";

const C = {
    speed: "#3987e5",
    power: "#d95926",
    cadence: "#199e70",
    hr: "#e66767",
    altitude: "#64748b",
    grid: "#2b3a4f",
    axis: "#64748b",
    textPrimary: "#f1f5f9",
    textSecondary: "#94a3b8",
};

const formatDuration = (sec: number) => {
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    if (h > 0) return `${h}시간 ${m}분`;
    if (m > 0) return s > 0 ? `${m}분 ${s}초` : `${m}분`;
    return `${s}초`;
};

type StreamKey = "speed" | "power" | "cadence" | "hr" | "altitude";

const STREAMS: { key: StreamKey; label: string; unit: string; digits: number }[] = [
    {key: "speed", label: "속도", unit: "km/h", digits: 1},
    {key: "power", label: "파워", unit: "W", digits: 0},
    {key: "cadence", label: "케이던스", unit: "rpm", digits: 0},
    {key: "hr", label: "심박", unit: "bpm", digits: 0},
    {key: "altitude", label: "고도", unit: "m", digits: 0},
];

export default function ActivityAnalyticsPanel({activityId}: { activityId: number }) {
    const [data, setData] = useState<ActivityAnalytics | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        setError(null);
        fetchActivityAnalytics(activityId)
            .then(setData)
            .catch((e) => setError(e.message));
    }, [activityId, reloadKey]);

    return (
        <div style={S.card}>
            <div style={S.header}>
                <h3 style={S.title}>📈 라이딩 분석</h3>
                {data && <ZoneSettingEditor data={data} onSaved={() => setReloadKey((k) => k + 1)}/>}
            </div>

            {error && <div style={S.empty}>{error}</div>}
            {!data && !error && <div style={S.empty}>분석 데이터를 계산하는 중...</div>}

            {data && (
                <div style={S.body}>
                    <SummaryTiles data={data}/>
                    <StreamCharts streams={data.streams}/>

                    <div style={S.twoCol}>
                        <ZoneBars title="⚡ 파워존" unit="W" zones={data.powerZones} color={C.power}
                                  emptyText="파워 데이터가 없습니다."/>
                        <ZoneBars title="❤️ 심박존" unit="bpm" zones={data.hrZones} color={C.hr}
                                  emptyText="심박 데이터가 없습니다."/>
                    </div>

                    <div style={S.twoCol}>
                        <PowerCurve data={data}/>
                        <Intervals data={data}/>
                    </div>
                </div>
            )}
        </div>
    );
}

// ── FTP / 최대심박 ────────────────────────────────────────────────

function ZoneSettingEditor({data, onSaved}: { data: ActivityAnalytics; onSaved: () => void }) {
    const z = data.zoneSetting;
    const [editing, setEditing] = useState(false);
    const [ftp, setFtp] = useState("");
    const [maxHr, setMaxHr] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const startEdit = () => {
        setFtp(z.ftpEstimated ? "" : String(z.ftp));
        setMaxHr(z.maxHrEstimated ? "" : String(z.maxHr));
        setError(null);
        setEditing(true);
    };

    const handleSave = async () => {
        setSaving(true);
        setError(null);
        try {
            await saveZoneSetting(ftp ? Number(ftp) : null, maxHr ? Number(maxHr) : null);
            setEditing(false);
            onSaved();
        } catch (e) {
            setError(e instanceof Error ? e.message : "저장 실패");
        } finally {
            setSaving(false);
        }
    };

    if (editing) {
        return (
            <div style={S.settingRow}>
                <label style={S.settingLabel}>
                    FTP <input style={S.settingInput} type="number" min={50} max={600} value={ftp}
                               placeholder={String(z.ftp)} onChange={(e) => setFtp(e.target.value)}/> W
                </label>
                <label style={S.settingLabel}>
                    최대심박 <input style={S.settingInput} type="number" min={120} max={230} value={maxHr}
                                placeholder={String(z.maxHr)} onChange={(e) => setMaxHr(e.target.value)}/> bpm
                </label>
                <button style={S.primaryBtn} onClick={handleSave} disabled={saving}>
                    {saving ? "저장 중..." : "저장"}
                </button>
                <button style={S.ghostBtn} onClick={() => setEditing(false)}>취소</button>
                {error && <span style={{color: C.hr, fontSize: 12}}>{error}</span>}
            </div>
        );
    }

    return (
        <div style={S.settingRow}>
            <span style={S.settingText}>
                FTP <b style={{color: C.textPrimary}}>{z.ftp}W</b>{z.ftpEstimated && " (추정)"}
                {" · "}최대심박 <b style={{color: C.textPrimary}}>{z.maxHr}</b>{z.maxHrEstimated && " (추정)"}
            </span>
            <AdminOnly>
                <button style={S.ghostBtn} onClick={startEdit}>✏️ 기준 설정</button>
            </AdminOnly>
        </div>
    );
}

// ── 요약 지표 ─────────────────────────────────────────────────────

function SummaryTiles({data}: { data: ActivityAnalytics }) {
    if (data.avgPower == null) return null;
    const tiles = [
        {label: "NP", val: data.normalizedPower != null ? `${data.normalizedPower} W` : "-", hint: "가변 강도를 반영한 평균 파워"},
        {label: "IF", val: data.intensityFactor?.toFixed(2) ?? "-", hint: "NP ÷ FTP"},
        {label: "TSS", val: data.trainingStress ?? "-", hint: "훈련 스트레스"},
        {label: "VI", val: data.variabilityIndex?.toFixed(2) ?? "-", hint: "NP ÷ 평균 파워 (1.0에 가까울수록 고른 페이스)"},
        {label: "일량", val: data.workKj != null ? `${data.workKj.toLocaleString()} kJ` : "-", hint: "총 일량"},
        {label: "평균 W/kg", val: data.wattsPerKg?.toFixed(2) ?? "-", hint: "평균 파워 ÷ 체중"},
    ];
    return (
        <div style={S.tiles}>
            {tiles.map((t) => (
                <div key={t.label} style={S.tile} title={t.hint}>
                    <div style={S.tileLabel}>{t.label}</div>
                    <div style={S.tileVal}>{t.val}</div>
                </div>
            ))}
        </div>
    );
}

// ── 거리축 그래프 (속도/파워/케이던스/심박/고도) ────────────────────

function StreamCharts({streams}: { streams: StreamPoint[] }) {
    const visible = STREAMS.filter((s) => streams.some((p) => p[s.key] != null && p[s.key] !== 0));
    if (visible.length === 0) return null;

    return (
        <div style={S.section}>
            {visible.map((s, i) => {
                const isLast = i === visible.length - 1;
                return (
                    <div key={s.key}>
                        <div style={S.streamLabel}>
                            <span style={{...S.swatch, background: C[s.key]}}/>
                            {s.label} <span style={S.streamUnit}>{s.unit}</span>
                        </div>
                        <ResponsiveContainer width="100%" height={isLast ? 96 : 72}>
                            {s.key === "altitude" ? (
                                <AreaChart data={streams} syncId="activity-streams" margin={CHART_MARGIN}>
                                    <CartesianGrid stroke={C.grid} vertical={false}/>
                                    <XAxis {...xAxisProps(isLast)}/>
                                    <YAxis {...yAxisProps}/>
                                    <Tooltip content={<StreamTooltip unit={s.unit} digits={s.digits}/>}
                                             cursor={{stroke: C.textSecondary}}/>
                                    <Area type="monotone" dataKey="altitude" stroke={C.altitude} strokeWidth={2}
                                          fill={C.altitude} fillOpacity={0.35} isAnimationActive={false}
                                          connectNulls/>
                                </AreaChart>
                            ) : (
                                <LineChart data={streams} syncId="activity-streams" margin={CHART_MARGIN}>
                                    <CartesianGrid stroke={C.grid} vertical={false}/>
                                    <XAxis {...xAxisProps(isLast)}/>
                                    <YAxis {...yAxisProps}/>
                                    <Tooltip content={<StreamTooltip unit={s.unit} digits={s.digits}/>}
                                             cursor={{stroke: C.textSecondary}}/>
                                    <Line type="monotone" dataKey={s.key} stroke={C[s.key]} strokeWidth={1.5}
                                          dot={false} activeDot={{r: 4, stroke: "#1e293b", strokeWidth: 2}}
                                          isAnimationActive={false} connectNulls/>
                                </LineChart>
                            )}
                        </ResponsiveContainer>
                    </div>
                );
            })}
        </div>
    );
}

const CHART_MARGIN = {top: 4, right: 8, bottom: 0, left: 0};

const xAxisProps = (showTicks: boolean) => ({
    dataKey: "km",
    type: "number" as const,
    domain: ["dataMin", "dataMax"] as [string, string],
    hide: !showTicks,
    tick: {fill: C.axis, fontSize: 11},
    tickLine: false,
    axisLine: {stroke: C.grid},
    tickFormatter: (v: number) => `${Math.round(v)}km`,
});

const yAxisProps = {
    width: 40,
    tick: {fill: C.axis, fontSize: 10},
    tickLine: false,
    axisLine: false,
    tickCount: 3,
    domain: ["auto", "auto"] as [string, string],
};

function StreamTooltip({active, payload, unit, digits}: {
    active?: boolean;
    payload?: { value?: number; payload?: StreamPoint }[];
    unit: string;
    digits: number;
}) {
    if (!active || !payload?.length || payload[0].value == null) return null;
    const p = payload[0];
    return (
        <div style={S.tooltip}>
            <span style={{color: C.textSecondary}}>{p.payload?.km.toFixed(1)} km</span>
            {"  "}
            <b>{Number(p.value).toFixed(digits)} {unit}</b>
        </div>
    );
}

// ── 존 분포 ───────────────────────────────────────────────────────

function ZoneBars({title, unit, zones, color, emptyText}: {
    title: string;
    unit: string;
    zones: ZoneTime[];
    color: string;
    emptyText: string;
}) {
    const maxPct = Math.max(...zones.map((z) => z.percent), 1);
    return (
        <div style={S.panel}>
            <div style={S.panelTitle}>{title}</div>
            {zones.length === 0 ? (
                <div style={S.emptySmall}>{emptyText}</div>
            ) : (
                zones.map((z) => (
                    <div key={z.zone} style={S.zoneRow}
                         title={`${z.zone} ${z.name}: ${formatDuration(z.seconds)} (${z.percent}%)`}>
                        <div style={S.zoneName}>
                            <b style={{color: C.textPrimary}}>{z.zone}</b> {z.name}
                            <div style={S.zoneRange}>
                                {z.max != null ? `${z.min}–${z.max}` : `${z.min}+`} {unit}
                            </div>
                        </div>
                        <div style={S.zoneTrack}>
                            <div style={{
                                ...S.zoneBar,
                                width: `${(z.percent / maxPct) * 100}%`,
                                background: color,
                            }}/>
                        </div>
                        <div style={S.zoneVal}>
                            <b style={{color: C.textPrimary}}>{z.percent}%</b>
                            <div style={S.zoneRange}>{formatDuration(z.seconds)}</div>
                        </div>
                    </div>
                ))
            )}
        </div>
    );
}

// ── 파워커브 ──────────────────────────────────────────────────────

function PowerCurve({data}: { data: ActivityAnalytics }) {
    const ftp = data.zoneSetting.ftp;
    return (
        <div style={S.panel}>
            <div style={S.panelTitle}>📉 파워커브 <span style={S.panelHint}>구간별 최고 평균 파워</span></div>
            {data.powerCurve.length === 0 ? (
                <div style={S.emptySmall}>파워 데이터가 없습니다.</div>
            ) : (
                <ResponsiveContainer width="100%" height={220}>
                    <LineChart data={data.powerCurve} margin={{top: 16, right: 16, bottom: 0, left: 0}}>
                        <CartesianGrid stroke={C.grid} vertical={false}/>
                        <XAxis dataKey="label" tick={{fill: C.axis, fontSize: 11}} tickLine={false}
                               axisLine={{stroke: C.grid}} interval={0} angle={0}/>
                        <YAxis width={40} tick={{fill: C.axis, fontSize: 10}} tickLine={false} axisLine={false}/>
                        <Tooltip
                            cursor={{stroke: C.textSecondary}}
                            content={({active, payload}) => {
                                if (!active || !payload?.length) return null;
                                const p = payload[0].payload as { label: string; watts: number };
                                return (
                                    <div style={S.tooltip}>
                                        <span style={{color: C.textSecondary}}>{p.label}</span>{"  "}
                                        <b>{p.watts} W</b>
                                        {ftp > 0 && <span style={{color: C.textSecondary}}> · FTP {Math.round(p.watts / ftp * 100)}%</span>}
                                    </div>
                                );
                            }}
                        />
                        <Line type="monotone" dataKey="watts" stroke={C.power} strokeWidth={2}
                              dot={{r: 4, fill: C.power, stroke: "#1e293b", strokeWidth: 2}}
                              activeDot={{r: 6, stroke: "#1e293b", strokeWidth: 2}} isAnimationActive={false}/>
                    </LineChart>
                </ResponsiveContainer>
            )}
        </div>
    );
}

// ── 인터벌 ────────────────────────────────────────────────────────

function Intervals({data}: { data: ActivityAnalytics }) {
    const threshold = Math.round(data.zoneSetting.ftp * 0.9);
    return (
        <div style={S.panel}>
            <div style={S.panelTitle}>
                🔁 인터벌 <span style={S.panelHint}>30초 평균 {threshold}W(FTP 90%) 이상 1분 이상 유지</span>
            </div>
            {data.intervals.length === 0 ? (
                <div style={S.emptySmall}>
                    {data.avgPower == null ? "파워 데이터가 없습니다." : "감지된 고강도 구간이 없습니다."}
                </div>
            ) : (
                <div className="lm-scroll-hidden" style={{maxHeight: 220, overflowY: "auto"}}>
                    <table style={S.table}>
                        <thead>
                        <tr>
                            {["#", "위치", "시간", "평균", "최대", "심박", "케이던스", "존"].map((h) => (
                                <th key={h} style={S.th}>{h}</th>
                            ))}
                        </tr>
                        </thead>
                        <tbody>
                        {data.intervals.map((it, i) => (
                            <tr key={i}>
                                <td style={S.td}>{i + 1}</td>
                                <td style={S.td}>{it.startKm.toFixed(1)} km</td>
                                <td style={S.td}>{formatDuration(it.durationSec)}</td>
                                <td style={{...S.td, color: C.textPrimary, fontWeight: 700}}>{it.avgPower} W</td>
                                <td style={S.td}>{it.maxPower} W</td>
                                <td style={S.td}>{it.avgHr ?? "-"}</td>
                                <td style={S.td}>{it.avgCadence ?? "-"}</td>
                                <td style={S.td}>{it.zone}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}

// ── 스타일 ────────────────────────────────────────────────────────

const S: Record<string, CSSProperties> = {
    card: {
        background: "#1e293b",
        borderRadius: 12,
        border: "1px solid #334155",
        marginBottom: 16,
    },
    header: {
        padding: "14px",
        borderBottom: "1px solid #334155",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: 8,
    },
    title: {margin: 0, fontSize: 14, fontWeight: 600, color: C.textPrimary},
    body: {padding: 14, display: "flex", flexDirection: "column", gap: 16},
    empty: {color: "#64748b", fontSize: 13, textAlign: "center", padding: "28px 0"},
    emptySmall: {color: "#64748b", fontSize: 12, textAlign: "center", padding: "20px 0"},

    settingRow: {display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap"},
    settingText: {fontSize: 12, color: C.textSecondary},
    settingLabel: {fontSize: 12, color: C.textSecondary, display: "flex", alignItems: "center", gap: 4},
    settingInput: {
        width: 64,
        padding: "4px 6px",
        background: "#0f172a",
        border: "1px solid #475569",
        borderRadius: 6,
        color: C.textPrimary,
        fontSize: 12,
    },
    primaryBtn: {
        padding: "4px 12px",
        background: "#3b82f6",
        border: "none",
        borderRadius: 6,
        color: "#fff",
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
    },
    ghostBtn: {
        padding: "4px 10px",
        background: "transparent",
        border: "1px solid #475569",
        borderRadius: 6,
        color: C.textSecondary,
        fontSize: 12,
        cursor: "pointer",
    },

    tiles: {display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(110px, 1fr))", gap: 8},
    tile: {background: "#0f172a", borderRadius: 10, padding: "10px 12px", border: "1px solid #334155"},
    tileLabel: {fontSize: 11, color: C.textSecondary},
    tileVal: {fontSize: 18, fontWeight: 700, color: C.textPrimary, marginTop: 2},

    section: {display: "flex", flexDirection: "column", gap: 4},
    streamLabel: {display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: C.textSecondary, paddingLeft: 40},
    swatch: {width: 10, height: 3, borderRadius: 2, display: "inline-block"},
    streamUnit: {color: "#64748b", fontSize: 11},

    twoCol: {display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 16},
    panel: {background: "#0f172a", borderRadius: 10, padding: 12, border: "1px solid #334155"},
    panelTitle: {fontSize: 13, fontWeight: 600, color: C.textPrimary, marginBottom: 10},
    panelHint: {fontSize: 11, fontWeight: 400, color: "#64748b", marginLeft: 6},

    zoneRow: {display: "grid", gridTemplateColumns: "96px 1fr 64px", alignItems: "center", gap: 8, padding: "4px 0"},
    zoneName: {fontSize: 12, color: C.textSecondary},
    zoneRange: {fontSize: 10, color: "#64748b", fontVariantNumeric: "tabular-nums"},
    zoneTrack: {height: 12, background: "#1e293b", borderRadius: 4},
    zoneBar: {height: "100%", borderRadius: "0 4px 4px 0", minWidth: 2},
    zoneVal: {fontSize: 12, textAlign: "right", fontVariantNumeric: "tabular-nums"},

    tooltip: {
        background: "#0f172a",
        border: "1px solid #334155",
        borderRadius: 6,
        padding: "4px 8px",
        fontSize: 12,
        color: C.textPrimary,
    },

    table: {width: "100%", borderCollapse: "collapse", fontSize: 12, fontVariantNumeric: "tabular-nums"},
    th: {
        position: "sticky",
        top: 0,
        background: "#0f172a",
        textAlign: "left",
        padding: "6px 6px",
        color: "#64748b",
        fontWeight: 600,
        borderBottom: "1px solid #334155",
    },
    td: {padding: "6px 6px", color: C.textSecondary, borderBottom: "1px solid #1e293b"},
};

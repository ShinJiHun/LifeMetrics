import { useState } from "react";

// ─── 타입 ───────────────────────────────────────────────
interface CheckPoint {
    id: string;
    name: string;
    distance: number;      // km
    elevation: number;     // 누적 획득고도 m
    targetArrival: string; // "HH:MM"
    deadline: string;      // "HH:MM"
    weather: {
        icon: string;
        temp: number;
        wind: number;
        desc: string;
    };
}

// ─── 더미 데이터 ─────────────────────────────────────────
const DUMMY_CPS: CheckPoint[] = [
    {
        id: "start",
        name: "출발 (수원)",
        distance: 0,
        elevation: 0,
        targetArrival: "06:00",
        deadline: "-",
        weather: { icon: "🌤️", temp: 12, wind: 2, desc: "맑음" },
    },
    {
        id: "cp1",
        name: "CP1 (천안)",
        distance: 88,
        elevation: 620,
        targetArrival: "12:00",
        deadline: "14:00",
        weather: { icon: "⛅", temp: 14, wind: 4, desc: "구름조금" },
    },
    {
        id: "cp2",
        name: "CP2 (대전)",
        distance: 180,
        elevation: 1240,
        targetArrival: "19:30",
        deadline: "22:00",
        weather: { icon: "🌧️", temp: 10, wind: 6, desc: "비" },
    },
    {
        id: "cp3",
        name: "CP3 (구미)",
        distance: 310,
        elevation: 2100,
        targetArrival: "07:00",
        deadline: "12:00",
        weather: { icon: "🌙", temp: 7, wind: 3, desc: "맑음(야간)" },
    },
    {
        id: "finish",
        name: "완주 (부산)",
        distance: 420,
        elevation: 2800,
        targetArrival: "18:00",
        deadline: "30:00",
        weather: { icon: "☀️", temp: 18, wind: 5, desc: "맑음" },
    },
];

// ─── 유틸 ────────────────────────────────────────────────
function diffKm(cps: CheckPoint[], idx: number) {
    if (idx === 0) return 0;
    return cps[idx].distance - cps[idx - 1].distance;
}

function paceColor(deadline: string, arrival: string) {
    if (deadline === "-") return "#22c55e";
    const [dh, dm] = deadline.split(":").map(Number);
    const [ah, am] = arrival.split(":").map(Number);
    const margin = dh * 60 + dm - (ah * 60 + am);
    if (margin >= 180) return "#22c55e";
    if (margin >= 60) return "#f59e0b";
    return "#ef4444";
}

// ─── 컴포넌트 ────────────────────────────────────────────
export default function BrevePlanPage() {
    const [step, setStep] = useState<"form" | "result">("form");
    const [expandedCp, setExpandedCp] = useState<string | null>(null);

    // 폼 상태
    const [eventName, setEventName] = useState("BRM 420 수원-부산");
    const [eventDate, setEventDate] = useState("2026-05-10");
    const [startTime, setStartTime] = useState("06:00");
    const [timeLimit, setTimeLimit] = useState("30");
    const [targetSpeed, setTargetSpeed] = useState("14");
    const [gpxFile, setGpxFile] = useState<File | null>(null);

    const cps = DUMMY_CPS;
    const totalDist = cps[cps.length - 1].distance;
    const totalElev = cps[cps.length - 1].elevation;

    return (
        <div style={styles.page}>
            {/* ── 헤더 ── */}
            <div style={styles.header}>
                <div style={styles.headerLeft}>
                    <span style={styles.headerIcon}>🏅</span>
                    <div>
                        <h1 style={styles.title}>랜도너스 계획</h1>
                        <p style={styles.subtitle}>Brevet Route Planner</p>
                    </div>
                </div>
                {step === "result" && (
                    <button style={styles.backBtn} onClick={() => setStep("form")}>
                        ← 다시 입력
                    </button>
                )}
            </div>

            {step === "form" ? (
                /* ════════════════════════════════
                   1단계: 입력 폼
                ════════════════════════════════ */
                <div style={styles.formCard}>
                    <h2 style={styles.sectionTitle}>대회 정보 입력</h2>

                    <div style={styles.formGrid}>
                        <div style={styles.formGroup}>
                            <label style={styles.label}>대회명</label>
                            <input
                                style={styles.input}
                                value={eventName}
                                onChange={(e) => setEventName(e.target.value)}
                                placeholder="예) BRM 600 서울-부산"
                            />
                        </div>

                        <div style={styles.formGroup}>
                            <label style={styles.label}>날짜</label>
                            <input
                                style={styles.input}
                                type="date"
                                value={eventDate}
                                onChange={(e) => setEventDate(e.target.value)}
                            />
                        </div>

                        <div style={styles.formGroup}>
                            <label style={styles.label}>출발 시간</label>
                            <input
                                style={styles.input}
                                type="time"
                                value={startTime}
                                onChange={(e) => setStartTime(e.target.value)}
                            />
                        </div>

                        <div style={styles.formGroup}>
                            <label style={styles.label}>제한 시간 (시간)</label>
                            <input
                                style={styles.input}
                                type="number"
                                value={timeLimit}
                                onChange={(e) => setTimeLimit(e.target.value)}
                                placeholder="예) 90"
                            />
                        </div>

                        <div style={styles.formGroup}>
                            <label style={styles.label}>목표 평균 속도 (km/h)</label>
                            <input
                                style={styles.input}
                                type="number"
                                value={targetSpeed}
                                onChange={(e) => setTargetSpeed(e.target.value)}
                                placeholder="예) 15"
                            />
                        </div>

                        <div style={styles.formGroup}>
                            <label style={styles.label}>GPX 파일</label>
                            <label style={styles.fileLabel}>
                                <input
                                    type="file"
                                    accept=".gpx"
                                    style={{ display: "none" }}
                                    onChange={(e) => setGpxFile(e.target.files?.[0] ?? null)}
                                />
                                {gpxFile ? `✅ ${gpxFile.name}` : "📎 GPX 파일 선택"}
                            </label>
                        </div>
                    </div>

                    <button style={styles.analyzeBtn} onClick={() => setStep("result")}>
                        🗺️ 구간 분석 시작
                    </button>
                </div>
            ) : (
                /* ════════════════════════════════
                   2단계: 분석 결과
                ════════════════════════════════ */
                <div>
                    {/* 요약 카드 */}
                    <div style={styles.summaryRow}>
                        <div style={styles.summaryCard}>
                            <span style={styles.summaryIcon}>🚴</span>
                            <div>
                                <div style={styles.summaryValue}>{totalDist} km</div>
                                <div style={styles.summaryLabel}>총 거리</div>
                            </div>
                        </div>
                        <div style={styles.summaryCard}>
                            <span style={styles.summaryIcon}>⛰️</span>
                            <div>
                                <div style={styles.summaryValue}>{totalElev.toLocaleString()} m</div>
                                <div style={styles.summaryLabel}>총 획득고도</div>
                            </div>
                        </div>
                        <div style={styles.summaryCard}>
                            <span style={styles.summaryIcon}>⏱️</span>
                            <div>
                                <div style={styles.summaryValue}>{timeLimit}시간</div>
                                <div style={styles.summaryLabel}>제한 시간</div>
                            </div>
                        </div>
                        <div style={styles.summaryCard}>
                            <span style={styles.summaryIcon}>📅</span>
                            <div>
                                <div style={styles.summaryValue}>{eventDate}</div>
                                <div style={styles.summaryLabel}>{startTime} 출발</div>
                            </div>
                        </div>
                    </div>

                    {/* 지도 플레이스홀더 */}
                    <div style={styles.mapPlaceholder}>
                        <div style={styles.mapInner}>
                            <span style={{ fontSize: 48 }}>🗺️</span>
                            <p style={{ margin: "8px 0 0", color: "#94a3b8" }}>
                                GPX 경로 지도 (Leaflet 연동 예정)
                            </p>
                            <p style={{ margin: "4px 0 0", color: "#64748b", fontSize: 13 }}>
                                CP 마커 · 경로 오버레이 · 고도 시각화
                            </p>
                        </div>
                        {/* CP 마커 범례 */}
                        <div style={styles.mapLegend}>
                            {cps.map((cp, i) => (
                                <div key={cp.id} style={styles.legendItem}>
                                    <div style={{
                                        ...styles.legendDot,
                                        background: i === 0 ? "#22c55e" : i === cps.length - 1 ? "#ef4444" : "#3b82f6"
                                    }} />
                                    <span style={{ fontSize: 12, color: "#94a3b8" }}>{cp.name}</span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* 구간 테이블 헤더 */}
                    <div style={styles.tableHeader}>
                        <h2 style={styles.sectionTitle}>구간별 계획표</h2>
                        <span style={{ color: "#64748b", fontSize: 13 }}>
                            목표 속도 {targetSpeed} km/h 기준
                        </span>
                    </div>

                    {/* CP 카드 목록 */}
                    <div style={styles.cpList}>
                        {cps.map((cp, idx) => {
                            const segDist = diffKm(cps, idx);
                            const isExpanded = expandedCp === cp.id;
                            const dotColor = idx === 0 ? "#22c55e" : idx === cps.length - 1 ? "#ef4444" : "#3b82f6";
                            const marginColor = paceColor(cp.deadline, cp.targetArrival);

                            return (
                                <div key={cp.id} style={styles.cpRow}>
                                    {/* 타임라인 선 */}
                                    <div style={styles.timelineCol}>
                                        <div style={{ ...styles.timelineDot, background: dotColor }} />
                                        {idx < cps.length - 1 && <div style={styles.timelineLine} />}
                                    </div>

                                    {/* 카드 */}
                                    <div style={styles.cpCard}>
                                        {/* 카드 헤더 */}
                                        <div
                                            style={styles.cpCardHeader}
                                            onClick={() => setExpandedCp(isExpanded ? null : cp.id)}
                                        >
                                            <div style={styles.cpCardLeft}>
                                                <span style={{ ...styles.cpBadge, background: dotColor }}>
                                                    {idx === 0 ? "출발" : idx === cps.length - 1 ? "완주" : `CP${idx}`}
                                                </span>
                                                <div>
                                                    <div style={styles.cpName}>{cp.name}</div>
                                                    <div style={styles.cpMeta}>
                                                        {cp.distance} km 지점
                                                        {segDist > 0 && <span style={styles.cpSegDist}> · 이번 구간 +{segDist} km</span>}
                                                    </div>
                                                </div>
                                            </div>

                                            <div style={styles.cpCardRight}>
                                                {/* 날씨 */}
                                                <div style={styles.weatherChip}>
                                                    <span>{cp.weather.icon}</span>
                                                    <span style={{ fontWeight: 600 }}>{cp.weather.temp}°C</span>
                                                    <span style={{ color: "#94a3b8" }}>💨{cp.weather.wind}m/s</span>
                                                </div>

                                                {/* 시간 */}
                                                <div style={styles.timeChips}>
                                                    <div style={styles.timeChip}>
                                                        <span style={styles.timeLabel}>목표</span>
                                                        <span style={{ ...styles.timeValue, color: "#e2e8f0" }}>
                                                            {cp.targetArrival}
                                                        </span>
                                                    </div>
                                                    <div style={styles.timeChip}>
                                                        <span style={styles.timeLabel}>마감</span>
                                                        <span style={{ ...styles.timeValue, color: marginColor }}>
                                                            {cp.deadline}
                                                        </span>
                                                    </div>
                                                </div>

                                                <span style={styles.expandIcon}>{isExpanded ? "▲" : "▼"}</span>
                                            </div>
                                        </div>

                                        {/* 펼쳐지는 상세 */}
                                        {isExpanded && (
                                            <div style={styles.cpDetail}>
                                                {/* 고도 프로파일 플레이스홀더 */}
                                                <div style={styles.elevPlaceholder}>
                                                    <span style={{ color: "#64748b", fontSize: 13 }}>
                                                        📈 고도 프로파일 (recharts 연동 예정)
                                                    </span>
                                                </div>

                                                {/* 시간대별 날씨 */}
                                                <div style={styles.hourlyWeather}>
                                                    <div style={styles.detailLabel}>시간대별 날씨</div>
                                                    <div style={styles.hourlyRow}>
                                                        {["06:00", "09:00", "12:00", "15:00"].map((t) => (
                                                            <div key={t} style={styles.hourlyCell}>
                                                                <span style={{ fontSize: 12, color: "#94a3b8" }}>{t}</span>
                                                                <span style={{ fontSize: 18 }}>{cp.weather.icon}</span>
                                                                <span style={{ fontSize: 13, fontWeight: 600 }}>{cp.weather.temp}°</span>
                                                                <span style={{ fontSize: 11, color: "#64748b" }}>💨{cp.weather.wind}</span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                </div>

                                                {/* 페이스 정보 */}
                                                {segDist > 0 && (
                                                    <div style={styles.paceInfo}>
                                                        <div style={styles.paceItem}>
                                                            <span style={styles.paceLabel}>구간 거리</span>
                                                            <span style={styles.paceValue}>{segDist} km</span>
                                                        </div>
                                                        <div style={styles.paceItem}>
                                                            <span style={styles.paceLabel}>예상 소요</span>
                                                            <span style={styles.paceValue}>
                                                                {Math.round(segDist / Number(targetSpeed) * 60)}분
                                                            </span>
                                                        </div>
                                                        <div style={styles.paceItem}>
                                                            <span style={styles.paceLabel}>마감 여유</span>
                                                            <span style={{ ...styles.paceValue, color: marginColor }}>
                                                                {cp.deadline !== "-" ? (() => {
                                                                    const [dh, dm] = cp.deadline.split(":").map(Number);
                                                                    const [ah, am] = cp.targetArrival.split(":").map(Number);
                                                                    const m = dh * 60 + dm - (ah * 60 + am);
                                                                    return `${Math.floor(m / 60)}시간 ${m % 60}분`;
                                                                })() : "-"}
                                                            </span>
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
}

// ─── 스타일 ──────────────────────────────────────────────
const styles: Record<string, React.CSSProperties> = {
    page: {
        padding: "24px 24px 48px",
        maxWidth: 1100,
        fontFamily: "'Pretendard', 'Noto Sans KR', sans-serif",
        color: "#e2e8f0",
        background: "#0f172a",
        minHeight: "100vh",
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 28,
    },
    headerLeft: { display: "flex", alignItems: "center", gap: 14 },
    headerIcon: { fontSize: 36 },
    title: { margin: 0, fontSize: 24, fontWeight: 700, color: "#f1f5f9" },
    subtitle: { margin: "2px 0 0", fontSize: 13, color: "#64748b" },
    backBtn: {
        padding: "8px 16px",
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 8,
        color: "#94a3b8",
        cursor: "pointer",
        fontSize: 13,
    },

    // 폼
    formCard: {
        background: "#1e293b",
        borderRadius: 16,
        padding: 32,
        border: "1px solid #334155",
    },
    sectionTitle: { margin: "0 0 20px", fontSize: 17, fontWeight: 600, color: "#f1f5f9" },
    formGrid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 20,
        marginBottom: 28,
    },
    formGroup: { display: "flex", flexDirection: "column", gap: 6 },
    label: { fontSize: 13, color: "#94a3b8", fontWeight: 500 },
    input: {
        padding: "10px 14px",
        background: "#0f172a",
        border: "1px solid #334155",
        borderRadius: 8,
        color: "#e2e8f0",
        fontSize: 14,
        outline: "none",
    },
    fileLabel: {
        padding: "10px 14px",
        background: "#0f172a",
        border: "1px dashed #475569",
        borderRadius: 8,
        color: "#94a3b8",
        fontSize: 14,
        cursor: "pointer",
        textAlign: "center" as const,
    },
    analyzeBtn: {
        width: "100%",
        padding: "14px",
        background: "linear-gradient(135deg, #3b82f6, #6366f1)",
        border: "none",
        borderRadius: 10,
        color: "white",
        fontSize: 16,
        fontWeight: 700,
        cursor: "pointer",
        letterSpacing: "0.02em",
    },

    // 요약
    summaryRow: {
        display: "grid",
        gridTemplateColumns: "repeat(4, 1fr)",
        gap: 12,
        marginBottom: 20,
    },
    summaryCard: {
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 12,
        padding: "16px 20px",
        display: "flex",
        alignItems: "center",
        gap: 12,
    },
    summaryIcon: { fontSize: 28 },
    summaryValue: { fontSize: 18, fontWeight: 700, color: "#f1f5f9" },
    summaryLabel: { fontSize: 12, color: "#64748b", marginTop: 2 },

    // 지도
    mapPlaceholder: {
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 12,
        height: 240,
        marginBottom: 20,
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "0 32px",
    },
    mapInner: { textAlign: "center" as const, color: "#94a3b8" },
    mapLegend: { display: "flex", flexDirection: "column" as const, gap: 8 },
    legendItem: { display: "flex", alignItems: "center", gap: 8 },
    legendDot: { width: 10, height: 10, borderRadius: "50%" },

    // 테이블 헤더
    tableHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 12,
    },

    // CP 목록
    cpList: { display: "flex", flexDirection: "column" as const },
    cpRow: { display: "flex", gap: 0, alignItems: "flex-start" },

    // 타임라인
    timelineCol: {
        display: "flex",
        flexDirection: "column" as const,
        alignItems: "center",
        width: 32,
        paddingTop: 20,
        flexShrink: 0,
    },
    timelineDot: {
        width: 12, height: 12, borderRadius: "50%", flexShrink: 0,
    },
    timelineLine: {
        width: 2, flex: 1, background: "#334155", minHeight: 40, marginTop: 4,
    },

    // CP 카드
    cpCard: {
        flex: 1,
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 12,
        marginBottom: 8,
        overflow: "hidden",
    },
    cpCardHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "14px 18px",
        cursor: "pointer",
    },
    cpCardLeft: { display: "flex", alignItems: "center", gap: 12 },
    cpBadge: {
        padding: "3px 8px",
        borderRadius: 6,
        fontSize: 11,
        fontWeight: 700,
        color: "white",
        flexShrink: 0,
    },
    cpName: { fontSize: 15, fontWeight: 600, color: "#f1f5f9" },
    cpMeta: { fontSize: 12, color: "#64748b", marginTop: 2 },
    cpSegDist: { color: "#3b82f6" },
    cpCardRight: { display: "flex", alignItems: "center", gap: 16 },

    // 날씨 칩
    weatherChip: {
        display: "flex",
        alignItems: "center",
        gap: 6,
        background: "#0f172a",
        padding: "6px 12px",
        borderRadius: 8,
        fontSize: 13,
    },

    // 시간 칩
    timeChips: { display: "flex", gap: 8 },
    timeChip: { display: "flex", flexDirection: "column" as const, alignItems: "center" },
    timeLabel: { fontSize: 10, color: "#64748b" },
    timeValue: { fontSize: 14, fontWeight: 700 },
    expandIcon: { color: "#64748b", fontSize: 12, marginLeft: 4 },

    // 상세 펼침
    cpDetail: {
        padding: "0 18px 18px",
        borderTop: "1px solid #334155",
        paddingTop: 16,
    },
    elevPlaceholder: {
        background: "#0f172a",
        borderRadius: 8,
        height: 80,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        marginBottom: 16,
    },
    hourlyWeather: { marginBottom: 14 },
    detailLabel: { fontSize: 12, color: "#64748b", marginBottom: 8, fontWeight: 600 },
    hourlyRow: { display: "flex", gap: 8 },
    hourlyCell: {
        flex: 1,
        background: "#0f172a",
        borderRadius: 8,
        padding: "10px 8px",
        display: "flex",
        flexDirection: "column" as const,
        alignItems: "center",
        gap: 3,
    },
    paceInfo: { display: "flex", gap: 12 },
    paceItem: {
        flex: 1,
        background: "#0f172a",
        borderRadius: 8,
        padding: "10px 14px",
        display: "flex",
        flexDirection: "column" as const,
        gap: 4,
    },
    paceLabel: { fontSize: 11, color: "#64748b" },
    paceValue: { fontSize: 16, fontWeight: 700, color: "#e2e8f0" },
};

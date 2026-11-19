// 장비 변경 계획 — 자전거 소모품(체인·타이어·카세트 등) 교체 주기
// 사용 거리는 장착일 이후 그 자전거로 탄 라이딩 기록에서 자동으로 합산된다.

import {useEffect, useState} from "react";
import type {CSSProperties} from "react";
import AdminOnly from "@/components/common/AdminOnly";
import {fetchGearPlan, partMeta, PART_TYPES, replacePart, savePart} from "@/api/bikeParts";
import type {BikePlan, PartInput, PartStatus, PartType, PartUsage} from "@/api/bikeParts";

const today = () => new Date().toLocaleDateString("sv-SE"); // YYYY-MM-DD (로컬)

const STATUS: Record<PartStatus, { label: string; color: string; bg: string }> = {
    OVERDUE: {label: "교체 필요", color: "#b91c1c", bg: "#fee2e2"},
    SOON: {label: "곧 교체", color: "#b45309", bg: "#fef3c7"},
    OK: {label: "양호", color: "#15803d", bg: "#dcfce7"},
    NONE: {label: "주기 미설정", color: "#64748b", bg: "#f1f5f9"},
    REMOVED: {label: "교체됨", color: "#64748b", bg: "#f1f5f9"},
};

const newPart = (bikeId: number, type: PartType = "CHAIN"): PartInput => ({
    bikeId,
    partType: type,
    name: null,
    installedOn: today(),
    removedOn: null,
    initialKm: 0,
    intervalKm: partMeta(type).km ?? null,
    intervalMonths: partMeta(type).months ?? null,
    note: null,
});

const toInput = (p: PartUsage): PartInput => ({
    id: p.id, bikeId: p.bikeId, partType: p.partType, name: p.name, installedOn: p.installedOn,
    removedOn: p.removedOn, initialKm: p.initialKm, intervalKm: p.intervalKm,
    intervalMonths: p.intervalMonths, note: p.note,
});

const km = (v: number) => `${Math.round(v).toLocaleString()}km`;

export default function GearPlanPage() {
    const [plans, setPlans] = useState<BikePlan[]>([]);
    const [includeRetired, setIncludeRetired] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = () => {
        fetchGearPlan(includeRetired)
            .then((p) => { setPlans(p); setError(null); })
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    };
    useEffect(load, [includeRetired]);

    if (loading) return <div style={S.page}>불러오는 중…</div>;

    const active = plans.flatMap((p) => p.parts.map((part) => ({bike: p.bikeName, part})));
    const urgent = active.filter((a) => a.part.status === "OVERDUE" || a.part.status === "SOON");

    return (
        <div style={S.page}>
            <div style={S.titleRow}>
                <h2 style={{margin: 0}}>🔧 장비 변경 계획</h2>
                <label style={{fontSize: 13, color: "#475569", display: "flex", gap: 6, alignItems: "center"}}>
                    <input type="checkbox" checked={includeRetired} onChange={(e) => setIncludeRetired(e.target.checked)}/>
                    종료한 자전거도 보기
                </label>
            </div>
            <p style={S.desc}>
                소모품의 장착일과 교체 주기를 적어 두면, 그 뒤로 해당 자전거로 탄 라이딩 거리를 자동으로 합산해
                교체 시기를 알려 줍니다. 교체 예상일은 최근 90일 주행 속도로 추정합니다.
            </p>
            {error && <p style={{color: "#dc3545"}}>{error}</p>}

            <div style={{...S.card, marginBottom: 20, borderColor: urgent.length ? "#fca5a5" : "#bbf7d0"}}>
                {urgent.length === 0 ? (
                    <span style={{color: "#15803d"}}>✅ 지금 교체가 필요한 소모품이 없습니다.</span>
                ) : (
                    <>
                        <strong>⚠️ 교체 확인 필요 {urgent.length}건</strong>
                        <ul style={{margin: "6px 0 0", paddingLeft: 18, fontSize: 13}}>
                            {urgent.map(({bike, part}) => (
                                <li key={part.id}>
                                    {bike} · {partMeta(part.partType).label}{part.name ? ` (${part.name})` : ""} —{" "}
                                    <span style={{color: STATUS[part.status].color}}>{STATUS[part.status].label}</span>
                                    {part.remainingKm != null && (part.remainingKm > 0
                                        ? `, ${km(part.remainingKm)} 남음`
                                        : `, ${km(-part.remainingKm)} 초과`)}
                                </li>
                            ))}
                        </ul>
                    </>
                )}
            </div>

            {plans.length === 0 && <p style={S.empty}>등록된 자전거가 없습니다.</p>}
            {plans.map((plan) => <BikeSection key={plan.bikeId} plan={plan} onChanged={load}/>)}
        </div>
    );
}

function BikeSection({plan, onChanged}: { plan: BikePlan; onChanged: () => void }) {
    const [adding, setAdding] = useState(false);
    const [showHistory, setShowHistory] = useState(false);

    return (
        <section style={{marginBottom: 28, opacity: plan.retired ? 0.7 : 1}}>
            <div style={S.titleRow}>
                <h3 style={{margin: 0, fontSize: 16}}>
                    🚲 {plan.bikeName} {plan.retired && <span style={S.badge}>종료</span>}
                </h3>
                <span style={{fontSize: 12, color: "#64748b"}}>최근 90일 하루 평균 {plan.recentKmPerDay}km</span>
            </div>

            {plan.parts.length === 0 && !adding && (
                <p style={{...S.empty, marginTop: 8}}>등록된 소모품이 없습니다.</p>
            )}
            <div style={{...S.grid, marginTop: 10}}>
                {plan.parts.map((p) => <PartCard key={p.id} part={p} onChanged={onChanged}/>)}
                {adding && (
                    <PartForm initial={newPart(plan.bikeId)} onCancel={() => setAdding(false)}
                              onSaved={() => { setAdding(false); onChanged(); }}/>
                )}
            </div>

            <div style={{display: "flex", gap: 8, marginTop: 10}}>
                <AdminOnly>
                    {!adding && <button style={S.ghostBtn} onClick={() => setAdding(true)}>➕ 소모품 추가</button>}
                </AdminOnly>
                {plan.history.length > 0 && (
                    <button style={S.ghostBtn} onClick={() => setShowHistory((v) => !v)}>
                        📜 교체 이력 {plan.history.length}건 {showHistory ? "접기" : "보기"}
                    </button>
                )}
            </div>
            {showHistory && (
                <table style={S.table}>
                    <thead>
                    <tr><th style={S.th}>부품</th><th style={S.th}>사용 기간</th><th style={S.th}>사용 거리</th><th style={S.th}>메모</th></tr>
                    </thead>
                    <tbody>
                    {plan.history.map((h) => (
                        <tr key={h.id}>
                            <td style={S.td}>{partMeta(h.partType).icon} {partMeta(h.partType).label}{h.name ? ` · ${h.name}` : ""}</td>
                            <td style={S.td}>{h.installedOn} ~ {h.removedOn}</td>
                            <td style={S.td}>{km(h.usedKm)}</td>
                            <td style={S.td}>{h.note ?? ""}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            )}
        </section>
    );
}

function PartCard({part, onChanged}: { part: PartUsage; onChanged: () => void }) {
    const [mode, setMode] = useState<"view" | "edit" | "replace">("view");
    const meta = partMeta(part.partType);
    const st = STATUS[part.status];
    const pct = part.ratio == null ? 0 : Math.min(100, Math.round(part.ratio * 100));

    if (mode === "edit") {
        return <PartForm initial={toInput(part)} onCancel={() => setMode("view")}
                         onSaved={() => { setMode("view"); onChanged(); }}/>;
    }

    return (
        <div style={S.card}>
            <div style={{display: "flex", alignItems: "center", gap: 8}}>
                <strong style={{fontSize: 15}}>{meta.icon} {meta.label}</strong>
                <span style={{...S.badge, color: st.color, background: st.bg}}>{st.label}</span>
            </div>
            {part.name && <div style={S.meta}>{part.name}</div>}
            <div style={S.meta}>
                {km(part.usedKm)}{part.intervalKm ? ` / ${km(part.intervalKm)}` : ""} · {part.usedDays}일째
                {part.intervalMonths ? ` / ${part.intervalMonths}개월` : ""}
            </div>
            {part.ratio != null && (
                <div style={S.barBg}>
                    <div style={{...S.bar, width: `${pct}%`, background: st.color}}/>
                </div>
            )}
            <div style={S.meta}>📅 장착 {part.installedOn}</div>
            {part.expectedReplaceOn && <div style={S.meta}>🔁 교체 예상 {part.expectedReplaceOn}</div>}
            {part.note && <div style={S.meta}>📝 {part.note}</div>}

            {mode === "replace" ? (
                <ReplaceForm part={part} onCancel={() => setMode("view")}
                             onDone={() => { setMode("view"); onChanged(); }}/>
            ) : (
                <AdminOnly>
                    <div style={{display: "flex", gap: 6, marginTop: 10}}>
                        <button style={S.primaryBtn} onClick={() => setMode("replace")}>🔁 교체함</button>
                        <button style={S.ghostBtn} onClick={() => setMode("edit")}>✏️ 수정</button>
                    </div>
                </AdminOnly>
            )}
        </div>
    );
}

function ReplaceForm({part, onCancel, onDone}: { part: PartUsage; onCancel: () => void; onDone: () => void }) {
    const [date, setDate] = useState(today());
    const [name, setName] = useState(part.name ?? "");
    const [note, setNote] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const submit = async () => {
        setSaving(true);
        setError(null);
        try {
            await replacePart(part.id, {date, name: name || null, note: note || null});
            onDone();
        } catch (e) {
            setError(e instanceof Error ? e.message : "실패");
            setSaving(false);
        }
    };

    return (
        <div style={{marginTop: 10, paddingTop: 10, borderTop: "1px dashed #cbd5e1", display: "grid", gap: 6}}>
            <label style={S.label}>교체일 <input type="date" style={S.input} value={date} onChange={(e) => setDate(e.target.value)}/></label>
            <label style={S.label}>새 제품명 <input style={S.input} value={name} onChange={(e) => setName(e.target.value)}/></label>
            <label style={S.label}>메모 <input style={S.input} value={note} placeholder="예) 체인 체커 0.75 측정" onChange={(e) => setNote(e.target.value)}/></label>
            <div style={{display: "flex", gap: 6, alignItems: "center"}}>
                <button style={S.primaryBtn} onClick={submit} disabled={saving || !date}>{saving ? "저장 중…" : "교체 기록"}</button>
                <button style={S.ghostBtn} onClick={onCancel}>취소</button>
            </div>
            {error && <span style={{color: "#dc3545", fontSize: 13}}>{error}</span>}
        </div>
    );
}

function PartForm({initial, onCancel, onSaved}: { initial: PartInput; onCancel: () => void; onSaved: () => void }) {
    const [form, setForm] = useState<PartInput>(initial);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const set = <K extends keyof PartInput>(key: K, value: PartInput[K]) => setForm((f) => ({...f, [key]: value}));
    const num = (v: string) => (v === "" ? null : Number(v));

    const changeType = (type: PartType) => {
        // 새로 추가하는 중이면 종류에 맞는 일반 교체 주기로 채워 준다
        if (form.id) set("partType", type);
        else setForm((f) => ({...f, partType: type, intervalKm: partMeta(type).km ?? null, intervalMonths: partMeta(type).months ?? null}));
    };

    const handleSave = async () => {
        setSaving(true);
        setError(null);
        try {
            await savePart(form);
            onSaved();
        } catch (e) {
            setError(e instanceof Error ? e.message : "저장 실패");
            setSaving(false);
        }
    };

    return (
        <div style={{...S.card, borderColor: "#93c5fd", gridColumn: "1 / -1"}}>
            <div style={S.formGrid}>
                <label style={S.label}>종류
                    <select style={S.input} value={form.partType} onChange={(e) => changeType(e.target.value as PartType)}>
                        {PART_TYPES.map((t) => <option key={t.type} value={t.type}>{t.icon} {t.label}</option>)}
                    </select>
                </label>
                <label style={S.label}>제품명
                    <input style={S.input} value={form.name ?? ""} placeholder="예) 시마노 CN-M8100"
                           onChange={(e) => set("name", e.target.value || null)}/>
                </label>
                <label style={S.label}>장착일
                    <input type="date" style={S.input} value={form.installedOn} onChange={(e) => set("installedOn", e.target.value)}/>
                </label>
                <label style={S.label}>기록 전 이미 쓴 거리(km)
                    <input type="number" min={0} style={S.input} value={form.initialKm}
                           onChange={(e) => set("initialKm", Number(e.target.value) || 0)}/>
                </label>
                <label style={S.label}>교체 주기(km)
                    <input type="number" min={0} style={S.input} value={form.intervalKm ?? ""}
                           onChange={(e) => set("intervalKm", num(e.target.value))}/>
                </label>
                <label style={S.label}>교체 주기(개월)
                    <input type="number" min={0} style={S.input} value={form.intervalMonths ?? ""}
                           onChange={(e) => set("intervalMonths", num(e.target.value))}/>
                </label>
                {form.id && (
                    <label style={S.label}>탈거일 (교체 없이 뺀 경우)
                        <input type="date" style={S.input} value={form.removedOn ?? ""}
                               onChange={(e) => set("removedOn", e.target.value || null)}/>
                    </label>
                )}
                <label style={S.label}>메모
                    <input style={S.input} value={form.note ?? ""} onChange={(e) => set("note", e.target.value || null)}/>
                </label>
            </div>
            <div style={{display: "flex", gap: 8, marginTop: 12, alignItems: "center"}}>
                <button style={S.primaryBtn} onClick={handleSave} disabled={saving || !form.installedOn}>
                    {saving ? "저장 중…" : "저장"}
                </button>
                <button style={S.ghostBtn} onClick={onCancel}>취소</button>
                {error && <span style={{color: "#dc3545", fontSize: 13}}>{error}</span>}
            </div>
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: {maxWidth: 980, margin: "0 auto", padding: 24},
    titleRow: {display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, flexWrap: "wrap"},
    desc: {color: "#94a3b8", fontSize: 13, margin: "6px 0 16px"},
    empty: {color: "#94a3b8", fontSize: 13, margin: 0},
    grid: {display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 12},
    card: {border: "1px solid #e5e7eb", borderRadius: 10, padding: 14, background: "#fff"},
    meta: {color: "#64748b", fontSize: 13, marginTop: 4},
    badge: {fontSize: 11, padding: "1px 6px", borderRadius: 999, background: "#f1f5f9", color: "#64748b"},
    barBg: {height: 6, background: "#f1f5f9", borderRadius: 999, marginTop: 8, overflow: "hidden"},
    bar: {height: "100%", borderRadius: 999},
    table: {width: "100%", borderCollapse: "collapse", marginTop: 10, fontSize: 13},
    th: {textAlign: "left", padding: "6px 8px", borderBottom: "1px solid #e5e7eb", color: "#64748b", fontWeight: 500},
    td: {padding: "6px 8px", borderBottom: "1px solid #f1f5f9"},
    formGrid: {display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 10},
    label: {display: "flex", flexDirection: "column", gap: 4, fontSize: 12, color: "#475569"},
    input: {padding: "6px 8px", border: "1px solid #cbd5e1", borderRadius: 6, fontSize: 13, font: "inherit"},
    primaryBtn: {
        padding: "6px 14px", background: "#2563eb", border: "none", borderRadius: 8,
        color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer",
    },
    ghostBtn: {
        padding: "5px 12px", background: "#fff", border: "1px solid #cbd5e1", borderRadius: 8,
        color: "#475569", fontSize: 13, cursor: "pointer",
    },
};

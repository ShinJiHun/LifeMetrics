// 자전거 상세의 피팅 기록: 최신 피팅 값 + 직전 피팅 대비 변화, 이전 기록 이력
// 피팅 날짜부터 그 세팅이 적용된 것으로 본다.

import {useEffect, useState} from "react";
import type {CSSProperties} from "react";
import AdminOnly from "@/components/common/AdminOnly";
import {emptyFit, FIT_NUMBER_FIELDS, fetchBikeFits, saveBikeFit} from "@/api/bikeFit";
import type {BikeFit, FitNumberKey} from "@/api/bikeFit";

const fmt = (v: number | null | undefined, unit: string) => (v == null ? "-" : `${v}${unit === "°" ? "°" : ` ${unit}`}`);

export default function BikeFitSection({bikeId}: { bikeId: number | string }) {
    const [fits, setFits] = useState<BikeFit[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [editing, setEditing] = useState<BikeFit | null>(null);
    const [selected, setSelected] = useState(0);

    const load = () => {
        fetchBikeFits(bikeId)
            .then((list) => { setFits(list); setSelected(0); })
            .catch((e) => setError(e.message));
    };

    useEffect(load, [bikeId]);

    // 새 피팅은 직전 값을 미리 채워서 바뀐 값만 고치면 되게 한다
    const startNew = () => {
        const base = fits[0];
        setEditing(base ? {...base, id: undefined, fitDate: emptyFit().fitDate, fitter: base.fitter, notes: null} : emptyFit());
    };

    const current = fits[selected];
    const previous = fits[selected + 1];

    return (
        <section style={{marginBottom: 32}}>
            <div style={S.header}>
                <h3 style={{margin: 0}}>🪑 피팅 기록</h3>
                <AdminOnly>
                    {!editing && <button style={S.primaryBtn} onClick={startNew}>➕ 피팅 기록 추가</button>}
                </AdminOnly>
            </div>
            <p style={S.desc}>피팅 날짜부터 그 세팅이 적용된 것으로 기록합니다. 길이는 mm, 각도는 °.</p>

            {error && <p style={{color: "#dc3545", fontSize: 13}}>{error}</p>}

            {editing && (
                <FitForm bikeId={bikeId} fit={editing} onCancel={() => setEditing(null)}
                         onSaved={() => { setEditing(null); load(); }}/>
            )}

            {!editing && fits.length === 0 && !error && (
                <p style={S.empty}>아직 피팅 기록이 없습니다.</p>
            )}

            {!editing && current && (
                <>
                    {fits.length > 1 && (
                        <div style={S.dateTabs}>
                            {fits.map((f, i) => (
                                <button key={f.id} onClick={() => setSelected(i)}
                                        style={{...S.dateTab, ...(i === selected ? S.dateTabActive : {})}}>
                                    {f.fitDate}{i === 0 && " (현재)"}
                                </button>
                            ))}
                        </div>
                    )}

                    <div style={S.card}>
                        <div style={S.cardHeader}>
                            <div>
                                <strong>{current.fitDate}</strong>
                                {current.fitter && <span style={S.muted}> · {current.fitter}</span>}
                                {previous && <span style={S.muted}> · {previous.fitDate} 대비 변화 표시</span>}
                            </div>
                            <AdminOnly>
                                <button style={S.ghostBtn} onClick={() => setEditing(current)}>✏️ 수정</button>
                            </AdminOnly>
                        </div>

                        <table style={S.table}>
                            <tbody>
                            {FIT_NUMBER_FIELDS.map(({key, label, unit, hint}) => (
                                <FitRow key={key} label={label} hint={hint} unit={unit}
                                        value={current[key]} prev={previous?.[key]}/>
                            ))}
                            <TextRow label="안장 모델" value={current.saddleModel} prev={previous?.saddleModel}/>
                            <TextRow label="클리트 (왼쪽)" value={current.cleatLeft} prev={previous?.cleatLeft}/>
                            <TextRow label="클리트 (오른쪽)" value={current.cleatRight} prev={previous?.cleatRight}/>
                            </tbody>
                        </table>

                        {current.notes && <div style={S.notes}>{current.notes}</div>}
                    </div>
                </>
            )}
        </section>
    );
}

function FitRow({label, hint, unit, value, prev}: {
    label: string;
    hint?: string;
    unit: string;
    value: number | null;
    prev: number | null | undefined;
}) {
    const delta = value != null && prev != null ? Math.round((value - prev) * 10) / 10 : null;
    return (
        <tr>
            <td style={S.tdLabel}>
                {label}
                {hint && <div style={S.hint}>{hint}</div>}
            </td>
            <td style={S.tdValue}>{fmt(value, unit)}</td>
            <td style={S.tdDelta}>
                {delta != null && delta !== 0 && (
                    <span style={{color: "#334155"}}>{delta > 0 ? "▲" : "▼"} {Math.abs(delta)}{unit === "°" ? "°" : unit}</span>
                )}
                {delta === 0 && <span style={S.muted}>변화 없음</span>}
            </td>
        </tr>
    );
}

function TextRow({label, value, prev}: { label: string; value: string | null; prev: string | null | undefined }) {
    const changed = prev !== undefined && (prev ?? "") !== (value ?? "");
    return (
        <tr>
            <td style={S.tdLabel}>{label}</td>
            <td style={S.tdValue}>{value || "-"}</td>
            <td style={S.tdDelta}>{changed && <span style={{color: "#334155"}}>변경 (이전: {prev || "-"})</span>}</td>
        </tr>
    );
}

function FitForm({bikeId, fit, onCancel, onSaved}: {
    bikeId: number | string;
    fit: BikeFit;
    onCancel: () => void;
    onSaved: () => void;
}) {
    const [form, setForm] = useState<BikeFit>(fit);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const setNum = (key: FitNumberKey, raw: string) =>
        setForm((f) => ({...f, [key]: raw === "" ? null : Number(raw)}));
    const setText = (key: "fitter" | "saddleModel" | "cleatLeft" | "cleatRight" | "notes", raw: string) =>
        setForm((f) => ({...f, [key]: raw || null}));

    const handleSave = async () => {
        if (!form.fitDate) {
            setError("피팅 날짜를 입력하세요.");
            return;
        }
        setSaving(true);
        setError(null);
        try {
            await saveBikeFit(bikeId, form);
            onSaved();
        } catch (e) {
            setError(e instanceof Error ? e.message : "저장 실패");
            setSaving(false);
        }
    };

    return (
        <div style={{...S.card, borderColor: "#93c5fd"}}>
            <div style={S.formGrid}>
                <label style={S.label}>피팅 날짜 *
                    <input type="date" style={S.input} value={form.fitDate}
                           onChange={(e) => setForm((f) => ({...f, fitDate: e.target.value}))}/>
                </label>
                <label style={S.label}>피터 / 샵
                    <input style={S.input} value={form.fitter ?? ""} onChange={(e) => setText("fitter", e.target.value)}/>
                </label>
                {FIT_NUMBER_FIELDS.map(({key, label, unit, hint}) => (
                    <label key={key} style={S.label} title={hint}>
                        {label} ({unit})
                        <input type="number" step="0.1" style={S.input} value={form[key] ?? ""}
                               placeholder={hint} onChange={(e) => setNum(key, e.target.value)}/>
                    </label>
                ))}
                <label style={S.label}>안장 모델
                    <input style={S.input} value={form.saddleModel ?? ""}
                           onChange={(e) => setText("saddleModel", e.target.value)}/>
                </label>
                <label style={S.label}>클리트 (왼쪽)
                    <input style={S.input} value={form.cleatLeft ?? ""} placeholder="예) 중앙, 외회전 2°"
                           onChange={(e) => setText("cleatLeft", e.target.value)}/>
                </label>
                <label style={S.label}>클리트 (오른쪽)
                    <input style={S.input} value={form.cleatRight ?? ""} placeholder="예) 2mm 뒤, 0°"
                           onChange={(e) => setText("cleatRight", e.target.value)}/>
                </label>
            </div>
            <label style={{...S.label, marginTop: 10}}>메모
                <textarea rows={3} style={{...S.input, resize: "vertical"}} value={form.notes ?? ""}
                          placeholder="피터 코멘트, 불편했던 점, 다음 점검 시기 등"
                          onChange={(e) => setText("notes", e.target.value)}/>
            </label>
            <div style={{display: "flex", gap: 8, marginTop: 12, alignItems: "center"}}>
                <button style={S.primaryBtn} onClick={handleSave} disabled={saving}>{saving ? "저장 중…" : "저장"}</button>
                <button style={S.ghostBtn} onClick={onCancel}>취소</button>
                {error && <span style={{color: "#dc3545", fontSize: 13}}>{error}</span>}
            </div>
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    header: {display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, flexWrap: "wrap"},
    desc: {margin: "4px 0 16px", color: "#94a3b8", fontSize: 13},
    empty: {color: "#94a3b8", fontSize: 14},
    muted: {color: "#94a3b8", fontSize: 13},
    card: {border: "1px solid #e5e7eb", borderRadius: 10, padding: 16, background: "#fff"},
    cardHeader: {display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, marginBottom: 8, flexWrap: "wrap"},
    dateTabs: {display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 10},
    dateTab: {
        padding: "4px 10px", border: "1px solid #e5e7eb", borderRadius: 999, background: "#fff",
        color: "#64748b", fontSize: 12, cursor: "pointer",
    },
    dateTabActive: {background: "#1e293b", borderColor: "#1e293b", color: "#fff", fontWeight: 700},
    table: {width: "100%", borderCollapse: "collapse", fontSize: 14, fontVariantNumeric: "tabular-nums"},
    tdLabel: {padding: "8px 8px 8px 0", color: "#475569", borderBottom: "1px solid #f1f5f9", width: "40%"},
    tdValue: {padding: 8, fontWeight: 600, color: "#0f172a", borderBottom: "1px solid #f1f5f9"},
    tdDelta: {padding: 8, fontSize: 12, borderBottom: "1px solid #f1f5f9", textAlign: "right"},
    hint: {fontSize: 11, color: "#94a3b8"},
    notes: {marginTop: 12, padding: 10, background: "#f8fafc", borderRadius: 8, fontSize: 13, color: "#334155", whiteSpace: "pre-wrap"},
    formGrid: {display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: 10},
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

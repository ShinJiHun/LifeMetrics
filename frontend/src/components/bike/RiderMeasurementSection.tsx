// 피팅 데이터 페이지의 신체 치수: 최신 측정값 + 직전 측정 대비 변화, 측정일별 이력
// 자전거와 상관없는 몸 치수·발·신발 정보라 자전거 피팅(BikeFitSection)과 따로 둔다.

import {useEffect, useState} from "react";
import AdminOnly from "@/components/common/AdminOnly";
import {fitStyles as S} from "@/components/bike/BikeFitSection";
import {
    ARCH_LABEL, BODY_FIELDS, FOOT_FIELDS, emptyMeasurement, fetchRiderMeasurements, saveRiderMeasurement,
} from "@/api/riderMeasurement";
import type {ArchType, MeasureField, MeasureNumberKey, MeasureTextKey, RiderMeasurement} from "@/api/riderMeasurement";

const fmt = (v: number | null | undefined, unit: string) => (v == null ? "-" : unit === "EU" ? `EU ${v}` : `${v} ${unit}`);

export default function RiderMeasurementSection({onLoaded}: {
    /** 측정값을 불러올 때마다 최신순 목록을 넘긴다 (페이지의 참고 계산용) */
    onLoaded?: (list: RiderMeasurement[]) => void;
}) {
    const [list, setList] = useState<RiderMeasurement[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [editing, setEditing] = useState<RiderMeasurement | null>(null);
    const [selected, setSelected] = useState(0);

    const load = () => {
        fetchRiderMeasurements()
            .then((l) => { setList(l); setSelected(0); onLoaded?.(l); })
            .catch((e) => setError(e.message));
    };

    useEffect(load, []);

    // 새 측정은 직전 값을 미리 채워서 바뀐 값만 고치면 되게 한다
    const startNew = () => {
        const base = list[0];
        setEditing(base ? {...base, id: undefined, measureDate: emptyMeasurement().measureDate, notes: null} : emptyMeasurement());
    };

    const current = list[selected];
    const previous = list[selected + 1];

    return (
        <section style={{marginBottom: 32}}>
            <div style={S.header}>
                <h3 style={{margin: 0}}>🧍 신체 치수</h3>
                <AdminOnly>
                    {!editing && <button style={S.primaryBtn} onClick={startNew}>➕ 측정 추가</button>}
                </AdminOnly>
            </div>
            <p style={S.desc}>피팅에 쓰는 몸 치수와 발·신발 정보입니다. 측정일별로 쌓여서 변화를 볼 수 있습니다. 길이는 mm.</p>

            {error && <p style={{color: "#dc3545", fontSize: 13}}>{error}</p>}

            {editing && (
                <MeasurementForm value={editing} onCancel={() => setEditing(null)}
                                 onSaved={() => { setEditing(null); load(); }}/>
            )}

            {!editing && list.length === 0 && !error && <p style={S.empty}>아직 측정 기록이 없습니다.</p>}

            {!editing && current && (
                <>
                    {list.length > 1 && (
                        <div style={S.dateTabs}>
                            {list.map((m, i) => (
                                <button key={m.id} onClick={() => setSelected(i)}
                                        style={{...S.dateTab, ...(i === selected ? S.dateTabActive : {})}}>
                                    {m.measureDate}{i === 0 && " (현재)"}
                                </button>
                            ))}
                        </div>
                    )}

                    <div style={S.card}>
                        <div style={S.cardHeader}>
                            <div>
                                <strong>{current.measureDate}</strong>
                                {current.measuredBy && <span style={S.muted}> · {current.measuredBy}</span>}
                                {previous && <span style={S.muted}> · {previous.measureDate} 대비 변화 표시</span>}
                            </div>
                            <AdminOnly>
                                <button style={S.ghostBtn} onClick={() => setEditing(current)}>✏️ 수정</button>
                            </AdminOnly>
                        </div>

                        <h4 style={sub}>몸</h4>
                        <ValueTable fields={BODY_FIELDS} cur={current} prev={previous}/>
                        <table style={S.table}>
                            <tbody>
                            <TextRow label="유연성" value={current.flexibility} prev={previous?.flexibility}/>
                            </tbody>
                        </table>

                        <h4 style={sub}>발 · 신발</h4>
                        <ValueTable fields={FOOT_FIELDS} cur={current} prev={previous}/>
                        <table style={S.table}>
                            <tbody>
                            <TextRow label="아치" value={current.archType ? ARCH_LABEL[current.archType] : null}
                                     prev={previous === undefined ? undefined : previous.archType ? ARCH_LABEL[previous.archType] : null}/>
                            <TextRow label="클릿 슈즈" value={current.shoeModel} prev={previous?.shoeModel}/>
                            <TextRow label="깔창" value={current.insole} prev={previous?.insole}/>
                            </tbody>
                        </table>

                        {current.notes && <div style={S.notes}>{current.notes}</div>}
                    </div>
                </>
            )}
        </section>
    );
}

const sub = {margin: "16px 0 4px", fontSize: 13, color: "#64748b"} as const;

function ValueTable({fields, cur, prev}: { fields: MeasureField[]; cur: RiderMeasurement; prev?: RiderMeasurement }) {
    return (
        <table style={S.table}>
            <tbody>
            {fields.map(({key, label, unit, hint}) => {
                const value = cur[key];
                const before = prev?.[key];
                const delta = value != null && before != null ? Math.round((value - before) * 10) / 10 : null;
                return (
                    <tr key={key}>
                        <td style={S.tdLabel}>{label}{hint && <div style={S.hint}>{hint}</div>}</td>
                        <td style={S.tdValue}>{fmt(value, unit)}</td>
                        <td style={S.tdDelta}>
                            {delta != null && delta !== 0 && (
                                <span style={{color: "#334155"}}>{delta > 0 ? "▲" : "▼"} {Math.abs(delta)} {unit}</span>
                            )}
                            {delta === 0 && <span style={S.muted}>변화 없음</span>}
                        </td>
                    </tr>
                );
            })}
            </tbody>
        </table>
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

function MeasurementForm({value, onCancel, onSaved}: {
    value: RiderMeasurement;
    onCancel: () => void;
    onSaved: () => void;
}) {
    const [form, setForm] = useState<RiderMeasurement>(value);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const setNum = (key: MeasureNumberKey, raw: string) =>
        setForm((f) => ({...f, [key]: raw === "" ? null : Number(raw)}));
    const setText = (key: MeasureTextKey, raw: string) => setForm((f) => ({...f, [key]: raw || null}));

    const handleSave = async () => {
        if (!form.measureDate) {
            setError("측정일을 입력하세요.");
            return;
        }
        setSaving(true);
        setError(null);
        try {
            await saveRiderMeasurement(form);
            onSaved();
        } catch (e) {
            setError(e instanceof Error ? e.message : "저장 실패");
            setSaving(false);
        }
    };

    const numInputs = (fields: MeasureField[]) => fields.map(({key, label, unit, hint}) => (
        <label key={key} style={S.label} title={hint}>
            {label} ({unit})
            <input type="number" step="0.1" style={S.input} value={form[key] ?? ""}
                   placeholder={hint} onChange={(e) => setNum(key, e.target.value)}/>
        </label>
    ));

    return (
        <div style={{...S.card, borderColor: "#93c5fd"}}>
            <div style={S.formGrid}>
                <label style={S.label}>측정일 *
                    <input type="date" style={S.input} value={form.measureDate}
                           onChange={(e) => setForm((f) => ({...f, measureDate: e.target.value}))}/>
                </label>
                <label style={S.label}>측정한 곳
                    <input style={S.input} value={form.measuredBy ?? ""} placeholder="피팅샵 / 직접"
                           onChange={(e) => setText("measuredBy", e.target.value)}/>
                </label>
            </div>

            <h4 style={sub}>몸</h4>
            <div style={S.formGrid}>
                {numInputs(BODY_FIELDS)}
                <label style={S.label}>유연성
                    <input style={S.input} value={form.flexibility ?? ""} placeholder="예) 전굴 손바닥 닿음"
                           onChange={(e) => setText("flexibility", e.target.value)}/>
                </label>
            </div>

            <h4 style={sub}>발 · 신발</h4>
            <div style={S.formGrid}>
                {numInputs(FOOT_FIELDS)}
                <label style={S.label}>아치
                    <select style={S.input} value={form.archType ?? ""}
                            onChange={(e) => setForm((f) => ({...f, archType: (e.target.value || null) as ArchType | null}))}>
                        <option value="">-</option>
                        {(Object.keys(ARCH_LABEL) as ArchType[]).map((a) => <option key={a} value={a}>{ARCH_LABEL[a]}</option>)}
                    </select>
                </label>
                <label style={S.label}>클릿 슈즈
                    <input style={S.input} value={form.shoeModel ?? ""} onChange={(e) => setText("shoeModel", e.target.value)}/>
                </label>
                <label style={S.label}>깔창
                    <input style={S.input} value={form.insole ?? ""} onChange={(e) => setText("insole", e.target.value)}/>
                </label>
            </div>

            <label style={{...S.label, marginTop: 10}}>메모
                <textarea rows={3} style={{...S.input, resize: "vertical"}} value={form.notes ?? ""}
                          placeholder="불편한 부위, 다리 길이 차이, 부상 이력 등"
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

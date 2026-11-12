import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import {
    createHealthTreatment,
    deleteHealthTreatment,
    deleteTreatmentVisit,
    emptyHealthTreatment,
    extractTreatmentDocument,
    extractTreatmentVisits,
    fetchHealthTreatments,
    fetchTreatmentVisits,
    saveTreatmentVisits,
    updateHealthTreatment,
    type HealthTreatment,
    type TreatmentVisit,
    type TreatmentVisitItem,
} from "@/api/healthTreatment";

function todayKst(): string {
    return new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 10);
}

function daysBetween(from: string, to: string): number {
    return Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000);
}

function errorMessage(e: unknown, fallback: string): string {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
    return msg || fallback;
}

/** 치료·부상 기록 (관리자 전용). 일일·라이딩·웨이트 분석이 그 날짜에 진행 중인 치료를 함께 본다. */
export default function HealthTreatmentPage() {
    const [list, setList] = useState<HealthTreatment[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [editing, setEditing] = useState<HealthTreatment | null>(null);
    const [saving, setSaving] = useState(false);
    const [reading, setReading] = useState(false);
    const fileRef = useRef<HTMLInputElement>(null);
    const [visits, setVisits] = useState<TreatmentVisit[]>([]);
    /** 진료·처방 문서를 올릴 치료 id */
    const [visitTarget, setVisitTarget] = useState<number | null>(null);
    const [visitReading, setVisitReading] = useState<number | null>(null);
    const [preview, setPreview] = useState<{ treatmentId: number; visits: TreatmentVisit[] } | null>(null);
    const [previewSaving, setPreviewSaving] = useState(false);
    const visitFileRef = useRef<HTMLInputElement>(null);

    const load = async () => {
        setLoading(true);
        try {
            const [ts, vs] = await Promise.all([fetchHealthTreatments(), fetchTreatmentVisits()]);
            setList(ts);
            setVisits(vs);
            setError(null);
        } catch (e) {
            setError(errorMessage(e, "치료 기록을 불러오지 못했습니다."));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    const save = async () => {
        if (!editing) return;
        setSaving(true);
        try {
            if (editing.id) await updateHealthTreatment(editing.id, editing);
            else await createHealthTreatment(editing);
            setEditing(null);
            await load();
        } catch (e) {
            setError(errorMessage(e, "저장하지 못했습니다."));
        } finally {
            setSaving(false);
        }
    };

    const finish = async (t: HealthTreatment) => {
        const end = window.prompt(`"${t.name}" 치료 종료일 (YYYY-MM-DD)`, todayKst());
        if (!end || !t.id) return;
        try {
            await updateHealthTreatment(t.id, { ...t, endDate: end });
            await load();
        } catch (e) {
            setError(errorMessage(e, "종료 처리하지 못했습니다."));
        }
    };

    const remove = async (t: HealthTreatment) => {
        if (!t.id || !window.confirm(`"${t.name}" 기록을 삭제할까요?`)) return;
        try {
            await deleteHealthTreatment(t.id);
            await load();
        } catch (e) {
            setError(errorMessage(e, "삭제하지 못했습니다."));
        }
    };

    /**
     * 진단서를 읽어 폼을 채운다. 비어 있는 칸만 채우고 이미 적은 값은 덮어쓰지 않는다(메모는 뒤에 덧붙임).
     * 편집 중이 아니면 새 기록으로 연다.
     */
    const readDocument = async (files: File[]) => {
        if (files.length === 0) return;
        setReading(true);
        setError(null);
        try {
            const doc = await extractTreatmentDocument(files);
            setEditing((prev) => {
                const base = prev ?? { ...emptyHealthTreatment(), startDate: "" };
                const pick = <K extends keyof HealthTreatment>(k: K) => (base[k] ? base[k] : (doc[k] ?? base[k])) as HealthTreatment[K];
                return {
                    ...base,
                    name: pick("name"),
                    diagnosisCode: pick("diagnosisCode"),
                    bodyPart: pick("bodyPart"),
                    startDate: base.startDate || doc.startDate || todayKst(),
                    endDate: pick("endDate"),
                    restrictions: pick("restrictions"),
                    notes: base.notes && doc.notes ? `${base.notes}\n${doc.notes}` : base.notes || doc.notes,
                    sourceFile: doc.sourceFile,
                };
            });
        } catch (e) {
            setError(errorMessage(e, "진단서를 읽지 못했습니다. 직접 입력해 주세요."));
        } finally {
            setReading(false);
            if (fileRef.current) fileRef.current.value = "";
        }
    };

    const pickVisitDocument = (treatmentId: number) => {
        setVisitTarget(treatmentId);
        visitFileRef.current?.click();
    };

    /** 처방전·영수증·세부내역서(여러 장 한 번에)를 읽어 날짜별 진료 미리보기를 띄운다(아직 저장 안 함). */
    const readVisitDocument = async (files: File[]) => {
        const treatmentId = visitTarget;
        if (!treatmentId || files.length === 0) return;
        setVisitReading(treatmentId);
        setError(null);
        try {
            const found = await extractTreatmentVisits(treatmentId, files);
            setPreview((prev) =>
                prev && prev.treatmentId === treatmentId
                    ? { treatmentId, visits: [...prev.visits, ...found].sort((a, b) => a.visitDate.localeCompare(b.visitDate)) }
                    : { treatmentId, visits: found },
            );
        } catch (e) {
            setError(errorMessage(e, "문서를 읽지 못했습니다."));
        } finally {
            setVisitReading(null);
            if (visitFileRef.current) visitFileRef.current.value = "";
        }
    };

    const savePreview = async () => {
        if (!preview) return;
        setPreviewSaving(true);
        try {
            await saveTreatmentVisits(preview.treatmentId, preview.visits);
            setPreview(null);
            await load();
        } catch (e) {
            setError(errorMessage(e, "진료 내역을 저장하지 못했습니다."));
        } finally {
            setPreviewSaving(false);
        }
    };

    const removeVisit = async (v: TreatmentVisit) => {
        if (!v.id || !window.confirm(`${v.visitDate} ${v.institution ?? ""} 진료 내역을 삭제할까요?`)) return;
        try {
            await deleteTreatmentVisit(v.id);
            await load();
        } catch (e) {
            setError(errorMessage(e, "삭제하지 못했습니다."));
        }
    };

    const setField = (key: keyof HealthTreatment, value: string) =>
        setEditing((prev) => (prev ? { ...prev, [key]: value === "" && key !== "name" && key !== "startDate" ? null : value } : prev));

    const today = todayKst();
    const active = list.filter((t) => !t.endDate || t.endDate >= today);
    const ended = list.filter((t) => t.endDate && t.endDate < today);

    return (
        <div style={S.page}>
            <div style={{ marginBottom: 24 }}>
                <div style={S.kicker}>ADMIN · 치료</div>
                <h1 style={S.title}>🩹 치료·부상</h1>
                <p style={S.subtitle}>
                    치료 중인 부상과 운동 제한을 적어 두면 일일 분석·라이딩 분석·웨이트 세션 분석이 그 기간의 기록을
                    치료 상황에 맞춰 해석합니다. 최근 14일 일일 분석은 다음 새벽 배치에서 자동으로 다시 분석됩니다.
                </p>
            </div>

            {error && <div style={S.errorBox}>{error}</div>}

            <input
                ref={fileRef}
                type="file"
                accept="application/pdf,image/jpeg,image/png,image/webp"
                multiple
                style={{ display: "none" }}
                onChange={(e) => readDocument(Array.from(e.target.files ?? []))}
            />
            <input
                ref={visitFileRef}
                type="file"
                accept="application/pdf,image/jpeg,image/png,image/webp"
                multiple
                style={{ display: "none" }}
                onChange={(e) => readVisitDocument(Array.from(e.target.files ?? []))}
            />
            {reading && <div style={S.infoBox}>📄 진단서를 읽는 중입니다… (10~30초)</div>}

            {editing ? (
                <TreatmentForm
                    value={editing}
                    saving={saving}
                    onField={setField}
                    reading={reading}
                    onReadDocument={() => fileRef.current?.click()}
                    onCancel={() => setEditing(null)}
                    onSave={save}
                />
            ) : (
                <>
                    <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
                        <button type="button" style={S.ghostBtn} disabled={reading} onClick={() => fileRef.current?.click()}>
                            📄 진단서로 추가
                        </button>
                        <button
                            type="button"
                            style={S.primaryBtn}
                            onClick={() => setEditing({ ...emptyHealthTreatment(), startDate: today })}
                        >
                            + 치료 추가
                        </button>
                    </div>

                    {loading ? (
                        <div>불러오는 중…</div>
                    ) : list.length === 0 ? (
                        <div style={{ color: "#8a90a3" }}>등록된 치료 기록이 없습니다.</div>
                    ) : (
                        <>
                            <h3 style={S.groupTitle}>치료 중 ({active.length})</h3>
                            {active.length === 0 && <div style={{ color: "#8a90a3", marginBottom: 20 }}>없음</div>}
                            {active.map((t) => (
                                <TreatmentCard
                                    key={t.id}
                                    t={t}
                                    today={today}
                                    onEdit={() => setEditing({ ...t })}
                                    onFinish={() => finish(t)}
                                    onDelete={() => remove(t)}
                                    visits={visits.filter((v) => v.treatmentId === t.id)}
                                    visitReading={visitReading === t.id}
                                    preview={preview && preview.treatmentId === t.id ? preview.visits : null}
                                    previewSaving={previewSaving}
                                    onAddVisitDocument={() => t.id && pickVisitDocument(t.id)}
                                    onDeleteVisit={removeVisit}
                                    onPreviewRemove={(i) =>
                                        setPreview((p) => (p ? { ...p, visits: p.visits.filter((_, j) => j !== i) } : p))
                                    }
                                    onPreviewSave={savePreview}
                                    onPreviewCancel={() => setPreview(null)}
                                />
                            ))}

                            {ended.length > 0 && (
                                <>
                                    <h3 style={{ ...S.groupTitle, marginTop: 28 }}>지난 치료 ({ended.length})</h3>
                                    {ended.map((t) => (
                                        <TreatmentCard
                                            key={t.id}
                                            t={t}
                                            today={today}
                                            onEdit={() => setEditing({ ...t })}
                                            onDelete={() => remove(t)}
                                            visits={visits.filter((v) => v.treatmentId === t.id)}
                                            visitReading={visitReading === t.id}
                                            preview={preview && preview.treatmentId === t.id ? preview.visits : null}
                                            previewSaving={previewSaving}
                                            onAddVisitDocument={() => t.id && pickVisitDocument(t.id)}
                                            onDeleteVisit={removeVisit}
                                            onPreviewRemove={(i) =>
                                                setPreview((p) => (p ? { ...p, visits: p.visits.filter((_, j) => j !== i) } : p))
                                            }
                                            onPreviewSave={savePreview}
                                            onPreviewCancel={() => setPreview(null)}
                                        />
                                    ))}
                                </>
                            )}
                        </>
                    )}
                </>
            )}
        </div>
    );
}

function TreatmentCard({
    t,
    today,
    onEdit,
    onFinish,
    onDelete,
    visits,
    visitReading,
    preview,
    previewSaving,
    onAddVisitDocument,
    onDeleteVisit,
    onPreviewRemove,
    onPreviewSave,
    onPreviewCancel,
}: {
    t: HealthTreatment;
    today: string;
    onEdit: () => void;
    onFinish?: () => void;
    onDelete: () => void;
    visits: TreatmentVisit[];
    visitReading: boolean;
    preview: TreatmentVisit[] | null;
    previewSaving: boolean;
    onAddVisitDocument: () => void;
    onDeleteVisit: (v: TreatmentVisit) => void;
    onPreviewRemove: (index: number) => void;
    onPreviewSave: () => void;
    onPreviewCancel: () => void;
}) {
    const ongoing = !t.endDate || t.endDate >= today;
    const period = t.endDate
        ? `${t.startDate} ~ ${t.endDate} (${daysBetween(t.startDate, t.endDate) + 1}일)`
        : `${t.startDate} ~ 진행 중 (${daysBetween(t.startDate, today)}일째)`;

    return (
        <section style={{ ...S.card, borderLeft: `4px solid ${ongoing ? "#ef4444" : "#d8dbe6"}` }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 10, flexWrap: "wrap" }}>
                <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 800, fontSize: 16, color: "#1b2236" }}>
                        {t.name}
                        {t.diagnosisCode && <span style={S.code}>{t.diagnosisCode}</span>}
                        {t.bodyPart && <span style={{ fontWeight: 500, fontSize: 13, color: "#6b7280" }}> · {t.bodyPart}</span>}
                    </div>
                    <div style={{ fontSize: 12.5, color: "#8a90a3", marginTop: 4 }}>
                        {period}
                        {t.sourceFile && <span> · 📄 {t.sourceFile}</span>}
                    </div>
                </div>
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    <button type="button" style={S.miniBtn} onClick={onEdit}>
                        수정
                    </button>
                    {onFinish && !t.endDate && (
                        <button type="button" style={S.miniBtn} onClick={onFinish}>
                            치료 종료
                        </button>
                    )}
                    <button type="button" style={S.miniDelBtn} onClick={onDelete}>
                        삭제
                    </button>
                </div>
            </div>
            {t.restrictions && (
                <div style={{ marginTop: 12 }}>
                    <div style={S.label}>운동 제한·권고</div>
                    <div style={S.text}>{t.restrictions}</div>
                </div>
            )}
            {t.notes && (
                <div style={{ marginTop: 10 }}>
                    <div style={S.label}>메모</div>
                    <div style={S.text}>{t.notes}</div>
                </div>
            )}

            <div style={{ marginTop: 16, paddingTop: 14, borderTop: "1px dashed #eceef5" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                    <div style={S.label}>진료·처방 내역 {visits.length > 0 && `(${visits.length}회)`}</div>
                    <button type="button" style={S.miniBtn} disabled={visitReading || previewSaving} onClick={onAddVisitDocument}>
                        {visitReading ? "읽는 중…" : "📄 처방전·영수증·세부내역서 추가"}
                    </button>
                </div>
                <div style={{ fontSize: 12, color: "#8a90a3", marginTop: 6 }}>
                    여러 장(세부내역서 여러 페이지, 처방전+영수증)은 한 번에 같이 선택하세요. 최대 20개, 사진은 자동으로 줄여서 올립니다.
                </div>
                {visitReading && (
                    <div style={{ ...S.infoBox, marginTop: 10, marginBottom: 0 }}>
                        📄 문서를 읽는 중입니다… 세부내역서처럼 긴 문서는 1분 넘게 걸릴 수 있습니다.
                    </div>
                )}

                {preview && (
                    <div style={S.previewBox}>
                        <div style={{ fontWeight: 700, fontSize: 13, color: "#3730a3", marginBottom: 8 }}>
                            문서에서 읽은 진료 {preview.length}건 — 확인 후 저장하세요
                        </div>
                        <div style={{ fontSize: 12, color: "#6b7280", marginBottom: 10 }}>
                            같은 날짜·기관·문서 종류의 기존 내역은 새 내용으로 바뀝니다. 파일과 개인정보는 저장하지 않습니다.
                            다른 문서를 더 올리면 아래에 이어 붙습니다.
                        </div>
                        {preview.map((v, i) => (
                            <VisitRow key={`${v.visitDate}-${i}`} v={v} onRemove={() => onPreviewRemove(i)} removeLabel="빼기" />
                        ))}
                        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 10 }}>
                            <button type="button" style={S.ghostBtn} onClick={onPreviewCancel} disabled={previewSaving}>
                                취소
                            </button>
                            <button
                                type="button"
                                style={S.primaryBtn}
                                onClick={onPreviewSave}
                                disabled={previewSaving || preview.length === 0}
                            >
                                {previewSaving ? "저장 중…" : `${preview.length}건 저장`}
                            </button>
                        </div>
                    </div>
                )}

                {visits.length > 0 ? (
                    <VisitList visits={visits} today={today} onDelete={onDeleteVisit} />
                ) : (
                    !preview && (
                        <div style={{ fontSize: 12.5, color: "#8a90a3", marginTop: 6 }}>
                            처방전, 약제비 영수증, 진료비 세부내역서, 건강보험 앱 처방조제내역 캡처를 올리면 날짜별로 정리되고 분석에도 반영됩니다.
                        </div>
                    )
                )}
            </div>
        </section>
    );
}

const CATEGORY_ICON: Record<string, string> = {
    진찰: "🩺",
    검사: "🔬",
    영상: "🩻",
    주사: "💉",
    물리치료: "⚡",
    처치: "🩹",
    약: "💊",
    재료: "📦",
    기타: "•",
};
const CATEGORY_ORDER = ["주사", "물리치료", "처치", "약", "검사", "영상", "진찰", "재료", "기타"];

function addDays(date: string, days: number): string {
    return new Date(Date.parse(date) + days * 86_400_000).toISOString().slice(0, 10);
}

function won(n: number | null | undefined): string {
    return n == null ? "" : `${n.toLocaleString("ko-KR")}원`;
}

function itemText(it: TreatmentVisitItem): string {
    let s = it.name;
    if (it.dose) s += ` ${it.dose}`;
    if (it.timesPerDay) s += `×${it.timesPerDay}회`;
    if (it.days) s += ` ${it.days}일분`;
    return s;
}

/** 진료 요약(횟수·최근 진료·복용 중인 약) + 날짜별 목록. 기본은 최근 3회만 보여 준다. */
function VisitList({ visits, today, onDelete }: { visits: TreatmentVisit[]; today: string; onDelete: (v: TreatmentVisit) => void }) {
    const [all, setAll] = useState(false);
    const sorted = [...visits].sort((a, b) => b.visitDate.localeCompare(a.visitDate) || (b.id ?? 0) - (a.id ?? 0));

    const counts: Record<string, number> = {};
    for (const v of visits) {
        for (const c of new Set(v.items.map((it) => it.category))) {
            if (c !== "진찰" && c !== "재료") counts[c] = (counts[c] ?? 0) + 1;
        }
    }
    const onMeds = visits.flatMap((v) =>
        v.items
            .filter((it) => it.category === "약" && it.days && addDays(v.visitDate, it.days - 1) >= today && v.visitDate <= today)
            .map((it) => ({ ...it, until: addDays(v.visitDate, (it.days ?? 1) - 1) })),
    );
    const paid = visits.reduce((sum, v) => sum + (v.patientAmount ?? 0), 0);
    const visitDays = new Set(visits.map((v) => v.visitDate)).size;

    return (
        <div style={{ marginTop: 10 }}>
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 10 }}>
                <span style={S.chip}>
                    진료일 {visitDays}일 · 최근 {sorted[0].visitDate}
                </span>
                {CATEGORY_ORDER.filter((c) => counts[c]).map((c) => (
                    <span key={c} style={S.chip}>
                        {CATEGORY_ICON[c]} {c} {counts[c]}회
                    </span>
                ))}
                {paid > 0 && <span style={S.chip}>본인부담 {won(paid)}</span>}
            </div>
            {onMeds.length > 0 && (
                <div style={{ ...S.infoBox, background: "#fff7ed", borderColor: "#fed7aa", color: "#9a3412", marginBottom: 10 }}>
                    💊 복용 중:{" "}
                    {onMeds.map((m, i) => (
                        <span key={i}>
                            {i > 0 && ", "}
                            {m.name}
                            {m.drugClass && ` (${m.drugClass})`} ~{m.until.slice(5)}
                        </span>
                    ))}
                </div>
            )}
            {(all ? sorted : sorted.slice(0, 3)).map((v) => (
                <VisitRow key={v.id} v={v} onRemove={() => onDelete(v)} removeLabel="삭제" />
            ))}
            {sorted.length > 3 && (
                <button type="button" style={{ ...S.miniBtn, marginTop: 4 }} onClick={() => setAll((x) => !x)}>
                    {all ? "접기" : `지난 진료 ${sorted.length - 3}회 더 보기`}
                </button>
            )}
        </div>
    );
}

function VisitRow({ v, onRemove, removeLabel }: { v: TreatmentVisit; onRemove: () => void; removeLabel: string }) {
    const byCategory = CATEGORY_ORDER.map((c) => [c, v.items.filter((it) => (CATEGORY_ORDER.includes(it.category) ? it.category : "기타") === c)] as const).filter(
        ([, items]) => items.length > 0,
    );
    return (
        <div style={S.visitRow}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "flex-start" }}>
                <div style={{ minWidth: 0 }}>
                    <span style={{ fontWeight: 700, fontSize: 13.5, color: "#1b2236" }}>{v.visitDate}</span>
                    {v.institution && <span style={{ fontSize: 13, color: "#374151" }}> · {v.institution}</span>}
                    {v.department && <span style={{ fontSize: 12, color: "#8a90a3" }}> ({v.department})</span>}
                    {v.docType && <span style={S.code}>{v.docType}</span>}
                </div>
                <div style={{ display: "flex", gap: 8, alignItems: "center", flexShrink: 0 }}>
                    {v.patientAmount != null && <span style={{ fontSize: 12, color: "#6b7280" }}>{won(v.patientAmount)}</span>}
                    <button type="button" style={S.linkDelBtn} onClick={onRemove}>
                        {removeLabel}
                    </button>
                </div>
            </div>
            {v.summary && <div style={{ fontSize: 12.5, color: "#4b5563", marginTop: 4 }}>{v.summary}</div>}
            {v.diagnosis && <div style={{ fontSize: 12, color: "#8a90a3", marginTop: 2 }}>상병: {v.diagnosis}</div>}
            {byCategory.length > 0 && (
                <div style={{ marginTop: 6, display: "flex", flexDirection: "column", gap: 3 }}>
                    {byCategory.map(([c, items]) => (
                        <div key={c} style={{ fontSize: 12.5, color: "#1b2236", lineHeight: 1.5 }}>
                            <span style={{ color: "#8a90a3", fontWeight: 600 }}>
                                {CATEGORY_ICON[c]} {c}
                            </span>{" "}
                            {items.map((it, i) => (
                                <span key={i}>
                                    {i > 0 && " · "}
                                    {itemText(it)}
                                    {it.drugClass && <span style={{ color: "#8a90a3" }}> ({it.drugClass})</span>}
                                </span>
                            ))}
                        </div>
                    ))}
                </div>
            )}
            {v.sourceFile && <div style={{ fontSize: 11, color: "#a1a6b5", marginTop: 4 }}>📄 {v.sourceFile}</div>}
        </div>
    );
}

function TreatmentForm({
    value,
    saving,
    reading,
    onReadDocument,
    onField,
    onCancel,
    onSave,
}: {
    value: HealthTreatment;
    saving: boolean;
    reading: boolean;
    onReadDocument: () => void;
    onField: (key: keyof HealthTreatment, value: string) => void;
    onCancel: () => void;
    onSave: () => void;
}) {
    const text = (key: keyof HealthTreatment) => (value[key] as string | null) ?? "";

    return (
        <section style={S.card}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
                <h3 style={{ ...S.groupTitle, margin: 0 }}>
                    {value.id ? "치료 기록 수정" : "새 치료 기록"}
                    {value.sourceFile && <span style={{ fontWeight: 400, fontSize: 12, color: "#8a90a3" }}> · 📄 {value.sourceFile}</span>}
                </h3>
                <button type="button" style={S.ghostBtn} disabled={reading || saving} onClick={onReadDocument}>
                    {reading ? "읽는 중…" : "📄 진단서에서 채우기"}
                </button>
            </div>
            {value.sourceFile && (
                <div style={{ ...S.infoBox, marginBottom: 14 }}>
                    진단서에서 읽은 값입니다. 저장 전에 확인해 주세요. 빈 칸만 채우고 이미 적은 값은 바꾸지 않습니다. 파일은 저장하지 않습니다.
                </div>
            )}
            <div style={S.grid}>
                <label style={S.field}>
                    <span style={S.label}>진단명/부상명 *</span>
                    <input
                        style={S.input}
                        placeholder="예: 장경인대 증후군"
                        value={text("name")}
                        onChange={(e) => onField("name", e.target.value)}
                    />
                </label>
                <label style={S.field}>
                    <span style={S.label}>상병코드</span>
                    <input
                        style={S.input}
                        placeholder="예: M76.3"
                        value={text("diagnosisCode")}
                        onChange={(e) => onField("diagnosisCode", e.target.value)}
                    />
                </label>
                <label style={S.field}>
                    <span style={S.label}>부위</span>
                    <input
                        style={S.input}
                        placeholder="예: 오른쪽 무릎 바깥쪽"
                        value={text("bodyPart")}
                        onChange={(e) => onField("bodyPart", e.target.value)}
                    />
                </label>
                <label style={S.field}>
                    <span style={S.label}>시작일 *</span>
                    <input style={S.input} type="date" value={text("startDate")} onChange={(e) => onField("startDate", e.target.value)} />
                </label>
                <label style={S.field}>
                    <span style={S.label}>종료일 (비우면 치료 중)</span>
                    <input style={S.input} type="date" value={text("endDate")} onChange={(e) => onField("endDate", e.target.value)} />
                </label>
                <label style={{ ...S.field, gridColumn: "1 / -1" }}>
                    <span style={S.label}>운동 제한·권고</span>
                    <textarea
                        style={{ ...S.input, minHeight: 64 }}
                        placeholder="예: 하체 고중량·러닝 자제, 라이딩은 저강도 고케이던스 위주"
                        value={text("restrictions")}
                        onChange={(e) => onField("restrictions", e.target.value)}
                    />
                </label>
                <label style={{ ...S.field, gridColumn: "1 / -1" }}>
                    <span style={S.label}>메모 (치료 내용·경과)</span>
                    <textarea
                        style={{ ...S.input, minHeight: 64 }}
                        placeholder="예: 주 2회 도수치료, 폼롤러 스트레칭"
                        value={text("notes")}
                        onChange={(e) => onField("notes", e.target.value)}
                    />
                </label>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 16 }}>
                <button type="button" style={S.ghostBtn} onClick={onCancel} disabled={saving}>
                    취소
                </button>
                <button
                    type="button"
                    style={S.primaryBtn}
                    onClick={onSave}
                    disabled={saving || !value.name.trim() || !value.startDate}
                >
                    {saving ? "저장 중…" : "저장"}
                </button>
            </div>
        </section>
    );
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 900, margin: "0 auto", padding: "36px 16px 100px" },
    kicker: { fontSize: 11, fontWeight: 700, letterSpacing: "0.14em", color: "#ef4444" },
    title: { margin: "6px 0 0", fontSize: 26, fontWeight: 800, color: "#1b2236" },
    subtitle: { margin: "8px 0 0", fontSize: 14, color: "#8a90a3", lineHeight: 1.6 },
    infoBox: { background: "#eef2ff", border: "1px solid #c7d2fe", color: "#3730a3", padding: "10px 14px", borderRadius: 8, marginBottom: 16, fontSize: 13 },
    code: { marginLeft: 8, padding: "1px 7px", borderRadius: 6, background: "#f3f4f6", color: "#4b5563", fontSize: 12, fontWeight: 600 },
    errorBox: { background: "#fef2f2", border: "1px solid #fecaca", color: "#b91c1c", padding: "10px 14px", borderRadius: 8, marginBottom: 16, fontSize: 13 },
    primaryBtn: { padding: "9px 16px", borderRadius: 8, border: "none", background: "#6366f1", color: "#fff", fontWeight: 700, fontSize: 13, cursor: "pointer" },
    ghostBtn: { padding: "9px 14px", borderRadius: 8, border: "1px solid #d8dbe6", background: "#fff", color: "#374151", fontWeight: 600, fontSize: 13, cursor: "pointer" },
    miniBtn: { padding: "4px 10px", borderRadius: 6, border: "1px solid #d8dbe6", background: "#fff", fontSize: 12, cursor: "pointer" },
    miniDelBtn: { padding: "4px 10px", borderRadius: 6, border: "1px solid #fecaca", background: "#fff", color: "#b91c1c", fontSize: 12, cursor: "pointer" },
    chip: { padding: "3px 9px", borderRadius: 999, background: "#f3f4f6", color: "#374151", fontSize: 12, fontWeight: 600 },
    previewBox: { marginTop: 10, padding: "12px 14px", borderRadius: 10, border: "1px solid #c7d2fe", background: "#f5f7ff" },
    visitRow: { padding: "10px 12px", borderRadius: 8, border: "1px solid #eceef5", background: "#fafbfd", marginBottom: 8 },
    linkDelBtn: { padding: 0, border: "none", background: "none", color: "#b91c1c", fontSize: 12, cursor: "pointer" },
    card: { background: "#fff", border: "1px solid #eceef5", borderRadius: 12, padding: "18px 20px", marginBottom: 14 },
    groupTitle: { margin: "0 0 14px", fontSize: 15, fontWeight: 800, color: "#1b2236" },
    grid: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 },
    field: { display: "flex", flexDirection: "column", gap: 4 },
    label: { fontSize: 12, fontWeight: 600, color: "#8a90a3" },
    text: { fontSize: 13.5, color: "#1b2236", whiteSpace: "pre-wrap", marginTop: 2 },
    input: { padding: "8px 10px", borderRadius: 8, border: "1px solid #d8dbe6", fontSize: 13, fontFamily: "inherit", background: "#fff", color: "#1b2236", width: "100%", boxSizing: "border-box" },
};

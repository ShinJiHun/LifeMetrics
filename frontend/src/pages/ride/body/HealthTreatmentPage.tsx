import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import {
    createHealthTreatment,
    deleteHealthTreatment,
    emptyHealthTreatment,
    fetchHealthTreatments,
    updateHealthTreatment,
    type HealthTreatment,
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

    const load = async () => {
        setLoading(true);
        try {
            setList(await fetchHealthTreatments());
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

            {editing ? (
                <TreatmentForm
                    value={editing}
                    saving={saving}
                    onField={setField}
                    onCancel={() => setEditing(null)}
                    onSave={save}
                />
            ) : (
                <>
                    <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 14 }}>
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
}: {
    t: HealthTreatment;
    today: string;
    onEdit: () => void;
    onFinish?: () => void;
    onDelete: () => void;
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
                        {t.bodyPart && <span style={{ fontWeight: 500, fontSize: 13, color: "#6b7280" }}> · {t.bodyPart}</span>}
                    </div>
                    <div style={{ fontSize: 12.5, color: "#8a90a3", marginTop: 4 }}>{period}</div>
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
        </section>
    );
}

function TreatmentForm({
    value,
    saving,
    onField,
    onCancel,
    onSave,
}: {
    value: HealthTreatment;
    saving: boolean;
    onField: (key: keyof HealthTreatment, value: string) => void;
    onCancel: () => void;
    onSave: () => void;
}) {
    const text = (key: keyof HealthTreatment) => (value[key] as string | null) ?? "";

    return (
        <section style={S.card}>
            <h3 style={S.groupTitle}>{value.id ? "치료 기록 수정" : "새 치료 기록"}</h3>
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
    errorBox: { background: "#fef2f2", border: "1px solid #fecaca", color: "#b91c1c", padding: "10px 14px", borderRadius: 8, marginBottom: 16, fontSize: 13 },
    primaryBtn: { padding: "9px 16px", borderRadius: 8, border: "none", background: "#6366f1", color: "#fff", fontWeight: 700, fontSize: 13, cursor: "pointer" },
    ghostBtn: { padding: "9px 14px", borderRadius: 8, border: "1px solid #d8dbe6", background: "#fff", color: "#374151", fontWeight: 600, fontSize: 13, cursor: "pointer" },
    miniBtn: { padding: "4px 10px", borderRadius: 6, border: "1px solid #d8dbe6", background: "#fff", fontSize: 12, cursor: "pointer" },
    miniDelBtn: { padding: "4px 10px", borderRadius: 6, border: "1px solid #fecaca", background: "#fff", color: "#b91c1c", fontSize: 12, cursor: "pointer" },
    card: { background: "#fff", border: "1px solid #eceef5", borderRadius: 12, padding: "18px 20px", marginBottom: 14 },
    groupTitle: { margin: "0 0 14px", fontSize: 15, fontWeight: 800, color: "#1b2236" },
    grid: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 },
    field: { display: "flex", flexDirection: "column", gap: 4 },
    label: { fontSize: 12, fontWeight: 600, color: "#8a90a3" },
    text: { fontSize: 13.5, color: "#1b2236", whiteSpace: "pre-wrap", marginTop: 2 },
    input: { padding: "8px 10px", borderRadius: 8, border: "1px solid #d8dbe6", fontSize: 13, fontFamily: "inherit", background: "#fff", color: "#1b2236", width: "100%", boxSizing: "border-box" },
};

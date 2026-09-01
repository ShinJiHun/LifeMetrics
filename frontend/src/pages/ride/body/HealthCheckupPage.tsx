import { useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";
import {
    createHealthCheckup,
    deleteHealthCheckup,
    emptyHealthCheckup,
    extractHealthCheckupPdf,
    fetchHealthCheckups,
    updateHealthCheckup,
    type HealthCheckup,
} from "@/api/healthCheckup";

type FieldType = "text" | "number" | "date" | "bool" | "textarea";
interface FieldDef {
    key: keyof HealthCheckup;
    label: string;
    type?: FieldType;
}
interface Group {
    title: string;
    fields: FieldDef[];
}

const GROUPS: Group[] = [
    {
        title: "메타 / 종합소견",
        fields: [
            { key: "checkupDate", label: "검진일", type: "date" },
            { key: "checkupPlace", label: "검진장소" },
            { key: "checkupOrg", label: "검진기관" },
            { key: "checkupDoctor", label: "판정의사" },
            { key: "overallJudgment", label: "종합판정" },
            { key: "extraExams", label: "그 외 받은 검사" },
            { key: "suspectedDisease", label: "의심 질환", type: "textarea" },
            { key: "existingDisease", label: "유질환", type: "textarea" },
            { key: "lifestyleAdvice", label: "생활습관 관리", type: "textarea" },
            { key: "etcAdvice", label: "기타 소견", type: "textarea" },
        ],
    },
    {
        title: "계측검사",
        fields: [
            { key: "heightCm", label: "키(cm)", type: "number" },
            { key: "weightKg", label: "몸무게(kg)", type: "number" },
            { key: "bmi", label: "체질량지수", type: "number" },
            { key: "bmiGrade", label: "비만 판정" },
            { key: "waistCm", label: "허리둘레(cm)", type: "number" },
            { key: "waistResult", label: "복부비만 판정" },
            { key: "visionLeft", label: "시력(좌)", type: "number" },
            { key: "visionRight", label: "시력(우)", type: "number" },
            { key: "visionCorrected", label: "교정시력", type: "bool" },
            { key: "hearingLeft", label: "청력(좌)" },
            { key: "hearingRight", label: "청력(우)" },
            { key: "hearingResult", label: "청력 판정" },
        ],
    },
    {
        title: "혈압",
        fields: [
            { key: "systolicBp", label: "수축기(mmHg)", type: "number" },
            { key: "diastolicBp", label: "이완기(mmHg)", type: "number" },
            { key: "bpResult", label: "혈압 판정" },
        ],
    },
    {
        title: "혈액검사",
        fields: [
            { key: "hemoglobin", label: "혈색소(g/dL)", type: "number" },
            { key: "anemiaResult", label: "빈혈 판정" },
            { key: "fastingBloodSugar", label: "공복혈당(mg/dL)", type: "number" },
            { key: "diabetesResult", label: "당뇨병 판정" },
            { key: "totalCholesterol", label: "총콜레스테롤", type: "number" },
            { key: "hdlCholesterol", label: "HDL 콜레스테롤", type: "number" },
            { key: "triglyceride", label: "중성지방", type: "number" },
            { key: "ldlCholesterol", label: "LDL 콜레스테롤", type: "number" },
            { key: "lipidResult", label: "이상지질혈증 판정" },
            { key: "serumCreatinine", label: "혈청 크레아티닌", type: "number" },
            { key: "egfr", label: "신사구체여과율(e-GFR)", type: "number" },
            { key: "kidneyResult", label: "신장질환 판정" },
            { key: "ast", label: "AST", type: "number" },
            { key: "alt", label: "ALT", type: "number" },
            { key: "ggt", label: "γ-GTP", type: "number" },
            { key: "liverResult", label: "간장질환 판정" },
        ],
    },
    {
        title: "요검사 / 영상검사",
        fields: [
            { key: "urineProteinResult", label: "요단백" },
            { key: "chestXrayResult", label: "흉부촬영" },
        ],
    },
    {
        title: "진찰(문진)",
        fields: [
            { key: "pastHistory", label: "과거병력" },
            { key: "medication", label: "약물치료" },
            { key: "needSmokingCessation", label: "금연 필요", type: "bool" },
            { key: "needDrinkingReduction", label: "절주 필요", type: "bool" },
            { key: "needPhysicalActivity", label: "신체활동 필요", type: "bool" },
            { key: "needStrengthExercise", label: "근력운동 필요", type: "bool" },
        ],
    },
    {
        title: "항목별 추가검사",
        fields: [
            { key: "hepBResult", label: "B형간염" },
            { key: "hepCResult", label: "C형간염" },
            { key: "depressionResult", label: "우울증" },
            { key: "depressionScore", label: "우울증 점수(PHQ-9)", type: "number" },
            { key: "psychosisResult", label: "조기정신증" },
            { key: "cognitiveResult", label: "인지기능장애" },
            { key: "boneDensityResult", label: "골밀도검사" },
            { key: "urinationResult", label: "배뇨장애" },
        ],
    },
];

export default function HealthCheckupPage() {
    const [list, setList] = useState<HealthCheckup[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [editing, setEditing] = useState<HealthCheckup | null>(null);
    const [viewing, setViewing] = useState<HealthCheckup | null>(null);
    const [extracting, setExtracting] = useState(false);
    const [saving, setSaving] = useState(false);
    const fileRef = useRef<HTMLInputElement>(null);

    const reload = async () => {
        setLoading(true);
        try {
            setList(await fetchHealthCheckups());
            setError(null);
        } catch (e) {
            setError(
                (e as { response?: { status?: number } })?.response?.status === 403
                    ? "관리자 로그인이 필요합니다."
                    : e instanceof Error
                      ? e.message
                      : String(e),
            );
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        reload();
    }, []);

    const startNew = () => {
        setViewing(null);
        setEditing(emptyHealthCheckup());
    };
    const startEdit = (c: HealthCheckup) => {
        setViewing(null);
        setEditing({ ...c });
    };
    const startView = (c: HealthCheckup) => {
        setEditing(null);
        setViewing(c);
    };

    const onUpload = async (file: File) => {
        setExtracting(true);
        setError(null);
        setViewing(null);
        try {
            const extracted = await extractHealthCheckupPdf(file);
            setEditing({ ...emptyHealthCheckup(), ...extracted });
        } catch (e) {
            setError(
                (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
                    "PDF 추출에 실패했습니다.",
            );
        } finally {
            setExtracting(false);
            if (fileRef.current) fileRef.current.value = "";
        }
    };

    const save = async () => {
        if (!editing) return;
        if (!editing.checkupDate) {
            setError("검진일은 필수입니다.");
            return;
        }
        setSaving(true);
        setError(null);
        try {
            if (editing.id) await updateHealthCheckup(editing.id, editing);
            else await createHealthCheckup(editing);
            setEditing(null);
            await reload();
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setSaving(false);
        }
    };

    const remove = async (c: HealthCheckup) => {
        if (!c.id || !confirm(`${c.checkupDate} 검진 기록을 삭제할까요?`)) return;
        await deleteHealthCheckup(c.id);
        await reload();
    };

    const setField = (key: keyof HealthCheckup, raw: string | boolean, type?: FieldType) => {
        setEditing((prev) => {
            if (!prev) return prev;
            let value: string | number | boolean | null;
            if (type === "bool") value = raw as boolean;
            else if (raw === "") value = null;
            else if (type === "number") value = Number(raw);
            else value = raw as string;
            return { ...prev, [key]: value };
        });
    };

    return (
        <div style={S.page}>
            <header style={{ marginBottom: 24 }}>
                <div style={S.kicker}>ADMIN ONLY · 신체 기록</div>
                <h1 style={S.title}>🩺 건강검진</h1>
                <p style={S.subtitle}>
                    국민건강보험공단 일반건강검진 결과통보서. PDF를 올리면 AI가 항목을 추출하고, 검토 후 저장합니다.
                </p>
            </header>

            {error && <div style={S.errorBox}>{error}</div>}

            {!editing && !viewing && (
                <>
                    <div style={{ display: "flex", gap: 10, marginBottom: 18, flexWrap: "wrap" }}>
                        <button type="button" style={S.primaryBtn} onClick={() => fileRef.current?.click()} disabled={extracting}>
                            {extracting ? "AI 추출 중…" : "📄 PDF 업로드 → AI 추출"}
                        </button>
                        <button type="button" style={S.ghostBtn} onClick={startNew}>
                            ✏️ 빈 양식으로 직접 입력
                        </button>
                        <input
                            ref={fileRef}
                            type="file"
                            accept="application/pdf"
                            style={{ display: "none" }}
                            onChange={(e) => e.target.files?.[0] && onUpload(e.target.files[0])}
                        />
                    </div>

                    {loading ? (
                        <div>불러오는 중…</div>
                    ) : list.length === 0 ? (
                        <div style={{ color: "#8a90a3" }}>저장된 건강검진 기록이 없습니다.</div>
                    ) : (
                        <div style={{ overflowX: "auto" }}>
                            <table style={S.table}>
                                <thead>
                                    <tr>
                                        <th style={S.th}>검진일</th>
                                        <th style={S.th}>종합판정</th>
                                        <th style={S.th}>BMI</th>
                                        <th style={S.th}>혈압</th>
                                        <th style={S.th}>공복혈당</th>
                                        <th style={S.th}>총콜레스테롤</th>
                                        <th style={S.th}></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {list.map((c) => (
                                        <tr key={c.id}>
                                            <td style={S.td}>{c.checkupDate}</td>
                                            <td style={S.td}>{c.overallJudgment ?? "-"}</td>
                                            <td style={S.td}>{c.bmi ?? "-"}</td>
                                            <td style={S.td}>
                                                {c.systolicBp && c.diastolicBp ? `${c.systolicBp}/${c.diastolicBp}` : "-"}
                                            </td>
                                            <td style={S.td}>{c.fastingBloodSugar ?? "-"}</td>
                                            <td style={S.td}>{c.totalCholesterol ?? "-"}</td>
                                            <td style={{ ...S.td, whiteSpace: "nowrap" }}>
                                                <button type="button" style={S.miniBtn} onClick={() => startView(c)}>
                                                    보기
                                                </button>{" "}
                                                <button type="button" style={S.miniBtn} onClick={() => startEdit(c)}>
                                                    수정
                                                </button>{" "}
                                                <button type="button" style={S.miniDelBtn} onClick={() => remove(c)}>
                                                    삭제
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </>
            )}

            {editing && (
                <CheckupForm
                    value={editing}
                    saving={saving}
                    onField={setField}
                    onCancel={() => setEditing(null)}
                    onSave={save}
                />
            )}

            {viewing && (
                <CheckupDetail
                    value={viewing}
                    onClose={() => setViewing(null)}
                    onEdit={() => {
                        const c = viewing;
                        setViewing(null);
                        setEditing({ ...c });
                    }}
                />
            )}
        </div>
    );
}

function CheckupDetail({ value, onClose, onEdit }: { value: HealthCheckup; onClose: () => void; onEdit: () => void }) {
    const [showRaw, setShowRaw] = useState(false);

    const fmt = (v: unknown, type?: FieldType): string => {
        if (v === null || v === undefined || v === "") return "-";
        if (type === "bool") return v ? "예" : "아니오";
        return String(v);
    };

    return (
        <div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
                <div style={{ fontWeight: 800, fontSize: 18 }}>
                    {value.checkupDate} 건강검진
                    {value.sourceFile && <span style={{ fontWeight: 400, fontSize: 12, color: "#8a90a3" }}> · {value.sourceFile}</span>}
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                    <button type="button" style={S.ghostBtn} onClick={onClose}>
                        닫기
                    </button>
                    <button type="button" style={S.primaryBtn} onClick={onEdit}>
                        수정
                    </button>
                </div>
            </div>

            {GROUPS.map((g) => {
                const rows = g.fields.filter((f) => {
                    const v = value[f.key];
                    return v !== null && v !== undefined && v !== "";
                });
                if (rows.length === 0) return null;
                return (
                    <section key={g.title} style={S.card}>
                        <h3 style={S.groupTitle}>{g.title}</h3>
                        <dl style={S.detailGrid}>
                            {rows.map((f) => (
                                <div key={f.key} style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                                    <dt style={S.label}>{f.label}</dt>
                                    <dd style={{ margin: 0, fontSize: 13.5, color: "#1b2236", whiteSpace: "pre-wrap" }}>
                                        {fmt(value[f.key], f.type)}
                                    </dd>
                                </div>
                            ))}
                        </dl>
                    </section>
                );
            })}

            {value.rawText && (
                <section style={S.card}>
                    <button type="button" style={S.ghostBtn} onClick={() => setShowRaw((s) => !s)}>
                        {showRaw ? "원본 텍스트 숨기기" : "원본 텍스트 보기 (raw_text)"}
                    </button>
                    {showRaw && <pre style={S.raw}>{value.rawText}</pre>}
                </section>
            )}
        </div>
    );
}

function CheckupForm({
    value,
    saving,
    onField,
    onCancel,
    onSave,
}: {
    value: HealthCheckup;
    saving: boolean;
    onField: (key: keyof HealthCheckup, raw: string | boolean, type?: FieldType) => void;
    onCancel: () => void;
    onSave: () => void;
}) {
    const [showRaw, setShowRaw] = useState(false);
    const rawText = useMemo(() => value.rawText ?? "", [value.rawText]);

    return (
        <div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
                <div style={{ fontWeight: 800, fontSize: 18 }}>
                    {value.id ? "건강검진 기록 수정" : "새 건강검진 기록"}
                    {value.sourceFile && <span style={{ fontWeight: 400, fontSize: 12, color: "#8a90a3" }}> · {value.sourceFile}</span>}
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                    <button type="button" style={S.ghostBtn} onClick={onCancel} disabled={saving}>
                        취소
                    </button>
                    <button type="button" style={S.primaryBtn} onClick={onSave} disabled={saving}>
                        {saving ? "저장 중…" : "저장"}
                    </button>
                </div>
            </div>

            {GROUPS.map((g) => (
                <section key={g.title} style={S.card}>
                    <h3 style={S.groupTitle}>{g.title}</h3>
                    <div style={S.grid}>
                        {g.fields.map((f) => {
                            const v = value[f.key];
                            if (f.type === "bool") {
                                return (
                                    <label key={f.key} style={{ ...S.field, flexDirection: "row", alignItems: "center", gap: 8 }}>
                                        <input
                                            type="checkbox"
                                            checked={v === true}
                                            onChange={(e) => onField(f.key, e.target.checked, "bool")}
                                        />
                                        <span style={S.label}>{f.label}</span>
                                    </label>
                                );
                            }
                            if (f.type === "textarea") {
                                return (
                                    <label key={f.key} style={{ ...S.field, gridColumn: "1 / -1" }}>
                                        <span style={S.label}>{f.label}</span>
                                        <textarea
                                            style={{ ...S.input, minHeight: 54 }}
                                            value={(v as string) ?? ""}
                                            onChange={(e) => onField(f.key, e.target.value, "textarea")}
                                        />
                                    </label>
                                );
                            }
                            return (
                                <label key={f.key} style={S.field}>
                                    <span style={S.label}>{f.label}</span>
                                    <input
                                        style={S.input}
                                        type={f.type === "date" ? "date" : f.type === "number" ? "number" : "text"}
                                        step={f.type === "number" ? "any" : undefined}
                                        value={v === null || v === undefined ? "" : String(v)}
                                        onChange={(e) => onField(f.key, e.target.value, f.type)}
                                    />
                                </label>
                            );
                        })}
                    </div>
                </section>
            ))}

            {rawText && (
                <section style={S.card}>
                    <button type="button" style={S.ghostBtn} onClick={() => setShowRaw((s) => !s)}>
                        {showRaw ? "원본 텍스트 숨기기" : "원본 텍스트 보기 (raw_text)"}
                    </button>
                    {showRaw && <pre style={S.raw}>{rawText}</pre>}
                </section>
            )}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 8 }}>
                <button type="button" style={S.ghostBtn} onClick={onCancel} disabled={saving}>
                    취소
                </button>
                <button type="button" style={S.primaryBtn} onClick={onSave} disabled={saving}>
                    {saving ? "저장 중…" : "저장"}
                </button>
            </div>
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 900, margin: "0 auto", padding: "36px 24px 100px" },
    kicker: { fontSize: 11, fontWeight: 700, letterSpacing: "0.14em", color: "#ef4444" },
    title: { margin: "6px 0 0", fontSize: 26, fontWeight: 800, color: "#1b2236" },
    subtitle: { margin: "8px 0 0", fontSize: 14, color: "#8a90a3", lineHeight: 1.6 },
    errorBox: { background: "#fef2f2", border: "1px solid #fecaca", color: "#b91c1c", padding: "10px 14px", borderRadius: 8, marginBottom: 16, fontSize: 13 },
    primaryBtn: { padding: "9px 16px", borderRadius: 8, border: "none", background: "#6366f1", color: "#fff", fontWeight: 700, fontSize: 13, cursor: "pointer" },
    ghostBtn: { padding: "9px 14px", borderRadius: 8, border: "1px solid #d8dbe6", background: "#fff", color: "#374151", fontWeight: 600, fontSize: 13, cursor: "pointer" },
    table: { width: "100%", borderCollapse: "collapse", fontSize: 13 },
    th: { textAlign: "left", padding: "8px 10px", borderBottom: "2px solid #e5e7eb", color: "#6b7280", fontWeight: 700 },
    td: { padding: "8px 10px", borderBottom: "1px solid #eef0f5" },
    miniBtn: { padding: "4px 10px", borderRadius: 6, border: "1px solid #d8dbe6", background: "#fff", fontSize: 12, cursor: "pointer" },
    miniDelBtn: { padding: "4px 10px", borderRadius: 6, border: "1px solid #fecaca", background: "#fff", color: "#b91c1c", fontSize: 12, cursor: "pointer" },
    card: { background: "#fff", border: "1px solid #eceef5", borderRadius: 12, padding: "18px 20px", marginBottom: 14 },
    groupTitle: { margin: "0 0 14px", fontSize: 15, fontWeight: 800, color: "#1b2236" },
    grid: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 },
    detailGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 12, margin: 0 },
    field: { display: "flex", flexDirection: "column", gap: 4 },
    label: { fontSize: 12, fontWeight: 600, color: "#8a90a3" },
    input: { padding: "8px 10px", borderRadius: 8, border: "1px solid #d8dbe6", fontSize: 13, fontFamily: "inherit", background: "#fff", color: "#1b2236", width: "100%", boxSizing: "border-box" },
    raw: { marginTop: 12, background: "#0f1420", color: "#c5cae0", padding: 14, borderRadius: 8, fontSize: 11.5, lineHeight: 1.6, whiteSpace: "pre-wrap", maxHeight: 360, overflow: "auto" },
};

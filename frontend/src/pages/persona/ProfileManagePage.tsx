import { useEffect, useState } from "react";
import type { CSSProperties, ReactNode } from "react";
import { Link } from "react-router-dom";
import {
    addCareerCompany,
    addCareerProject,
    addEducation,
    addIntroSection,
    deleteCareerCompany,
    deleteCareerProject,
    deleteEducation,
    deleteIntroSection,
    fetchProfile,
    updateCareerCompany,
    updateCareerProject,
    updateContact,
    updateEducation,
    updateIntro,
    updateIntroSection,
    type BasicProfile,
    type CareerCompany,
    type CareerProject,
    type Education,
    type IntroSection,
} from "@/api/profile";

const accent = "#6366F1";

export default function ProfileManagePage() {
    const [profile, setProfile] = useState<BasicProfile | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const reload = async () => {
        try {
            setProfile(await fetchProfile());
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        reload();
    }, []);

    if (loading) return <div style={S.page}>불러오는 중...</div>;
    if (error || !profile) return <div style={S.page}>프로필을 불러오지 못했습니다. {error}</div>;

    return (
        <div style={S.page}>
            <header style={{ marginBottom: 28 }}>
                <div style={{ ...S.kicker, color: accent }}>PROFILE MANAGER</div>
                <h1 style={S.title}>포트폴리오 프로필 관리</h1>
                <p style={S.subtitle}>
                    소개·경력·학력·연락처 내용을 수정합니다.{" "}
                    <Link to="/persona/developer" style={{ color: accent }}>
                        포트폴리오 페이지로 돌아가기 →
                    </Link>
                </p>
            </header>

            <IntroEditor intro={profile.intro} onChange={reload} />
            <ContactEditor contact={profile.contact} onChange={reload} />
            <CareerEditor career={profile.career} onChange={reload} />
            <EducationEditor education={profile.education} onChange={reload} />
        </div>
    );
}

// ── 소개 ─────────────────────────────────────────

function IntroEditor({ intro, onChange }: { intro: BasicProfile["intro"]; onChange: () => void }) {
    const [elevatorPitch, setElevatorPitch] = useState(intro.elevatorPitch);
    const [highlightsText, setHighlightsText] = useState(intro.highlights.join("\n"));
    const [saving, setSaving] = useState(false);

    const save = async () => {
        setSaving(true);
        try {
            await updateIntro({
                elevatorPitch,
                highlights: highlightsText.split("\n").map((l) => l.trim()).filter(Boolean),
            });
            onChange();
        } finally {
            setSaving(false);
        }
    };

    return (
        <section style={S.card}>
            <h2 style={S.sectionTitle}>소개</h2>

            <label style={S.label}>인용구 (elevator pitch)</label>
            <textarea style={{ ...S.input, minHeight: 60 }} value={elevatorPitch} onChange={(e) => setElevatorPitch(e.target.value)} />

            <label style={S.label}>핵심 성과 (한 줄에 하나씩)</label>
            <textarea style={{ ...S.input, minHeight: 90 }} value={highlightsText} onChange={(e) => setHighlightsText(e.target.value)} />

            <button type="button" onClick={save} disabled={saving} style={{ ...S.saveBtn, background: accent }}>
                {saving ? "저장 중..." : "소개 저장"}
            </button>

            <div style={{ marginTop: 20 }}>
                <h3 style={S.subSectionTitle}>소개 섹션 (소제목 + 문단)</h3>
                {intro.sections.map((s) => (
                    <IntroSectionRow key={s.id} section={s} onChange={onChange} />
                ))}
                <IntroSectionForm nextSortOrder={intro.sections.length} onChange={onChange} />
            </div>
        </section>
    );
}

function IntroSectionRow({ section, onChange }: { section: IntroSection; onChange: () => void }) {
    const [subtitle, setSubtitle] = useState(section.subtitle);
    const [linesText, setLinesText] = useState(section.lines.join("\n"));
    const [sortOrder, setSortOrder] = useState(section.sortOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updateIntroSection(section.id, {
                subtitle,
                lines: linesText.split("\n").map((l) => l.trim()).filter(Boolean),
                sortOrder,
            });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm(`"${section.subtitle}" 섹션을 삭제할까요?`)) return;
        setBusy(true);
        try {
            await deleteIntroSection(section.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={S.subCard}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={subtitle} onChange={(e) => setSubtitle(e.target.value)} placeholder="소제목" />
                <input
                    type="number"
                    style={{ ...S.input, marginBottom: 0, width: 70 }}
                    value={sortOrder}
                    onChange={(e) => setSortOrder(Number(e.target.value))}
                />
            </div>
            <textarea style={{ ...S.input, minHeight: 70, marginTop: 8 }} value={linesText} onChange={(e) => setLinesText(e.target.value)} placeholder="문단 (한 줄에 하나씩)" />
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>삭제</button>
            </div>
        </div>
    );
}

function IntroSectionForm({ nextSortOrder, onChange }: { nextSortOrder: number; onChange: () => void }) {
    const [subtitle, setSubtitle] = useState("");
    const [linesText, setLinesText] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!subtitle.trim()) return;
        setBusy(true);
        try {
            await addIntroSection({
                subtitle: subtitle.trim(),
                lines: linesText.split("\n").map((l) => l.trim()).filter(Boolean),
                sortOrder: nextSortOrder,
            });
            setSubtitle("");
            setLinesText("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <input style={{ ...S.input }} value={subtitle} onChange={(e) => setSubtitle(e.target.value)} placeholder="새 섹션 소제목" />
            <textarea style={{ ...S.input, minHeight: 60 }} value={linesText} onChange={(e) => setLinesText(e.target.value)} placeholder="문단 (한 줄에 하나씩)" />
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 섹션 추가</button>
        </div>
    );
}

// ── 연락처 ─────────────────────────────────────────

function ContactEditor({ contact, onChange }: { contact: BasicProfile["contact"]; onChange: () => void }) {
    const [phone, setPhone] = useState(contact.phone);
    const [github, setGithub] = useState(contact.github);
    const [blog, setBlog] = useState(contact.blog);
    const [saving, setSaving] = useState(false);

    const save = async () => {
        setSaving(true);
        try {
            await updateContact({ phone, github, blog });
            onChange();
        } finally {
            setSaving(false);
        }
    };

    return (
        <section style={S.card}>
            <h2 style={S.sectionTitle}>연락처</h2>
            <label style={S.label}>전화번호</label>
            <input style={S.input} value={phone} onChange={(e) => setPhone(e.target.value)} />
            <label style={S.label}>GitHub URL</label>
            <input style={S.input} value={github} onChange={(e) => setGithub(e.target.value)} />
            <label style={S.label}>블로그 URL</label>
            <input style={S.input} value={blog} onChange={(e) => setBlog(e.target.value)} />
            <button type="button" onClick={save} disabled={saving} style={{ ...S.saveBtn, background: accent }}>
                {saving ? "저장 중..." : "연락처 저장"}
            </button>
        </section>
    );
}

// ── 경력 ─────────────────────────────────────────

function CareerEditor({ career, onChange }: { career: CareerCompany[]; onChange: () => void }) {
    return (
        <section style={S.card}>
            <h2 style={S.sectionTitle}>경력 (회사 · 경력기술서)</h2>
            {career.map((c) => (
                <CareerCompanyCard key={c.id} company={c} onChange={onChange} />
            ))}
            <CareerCompanyForm nextSortOrder={career.length} onChange={onChange} />
        </section>
    );
}

function CareerCompanyCard({ company, onChange }: { company: CareerCompany; onChange: () => void }) {
    const [path, setPath] = useState(company.path);
    const [companyName, setCompanyName] = useState(company.companyName);
    const [periodLabel, setPeriodLabel] = useState(company.periodLabel);
    const [role, setRole] = useState(company.role);
    const [isCurrent, setIsCurrent] = useState(company.isCurrent);
    const [commitHash, setCommitHash] = useState(company.commitHash ?? "");
    const [commitTag, setCommitTag] = useState(company.commitTag ?? "");
    const [stackText, setStackText] = useState(company.stack.join(", "));
    const [sortOrder, setSortOrder] = useState(company.displayOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updateCareerCompany(company.id, {
                path,
                companyName,
                periodLabel,
                role,
                isCurrent,
                commitHash: commitHash.trim() || null,
                commitTag: commitTag.trim() || null,
                stack: stackText.split(",").map((s) => s.trim()).filter(Boolean),
                sortOrder,
            });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm(`"${company.companyName}" 회사와 그 안의 모든 프로젝트를 삭제할까요?`)) return;
        setBusy(true);
        try {
            await deleteCareerCompany(company.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={S.subCard}>
            <div style={S.grid2}>
                <Field label="path"><input style={S.input} value={path} onChange={(e) => setPath(e.target.value)} /></Field>
                <Field label="회사명"><input style={S.input} value={companyName} onChange={(e) => setCompanyName(e.target.value)} /></Field>
                <Field label="기간"><input style={S.input} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} /></Field>
                <Field label="직책"><input style={S.input} value={role} onChange={(e) => setRole(e.target.value)} /></Field>
                <Field label="commit hash"><input style={S.input} value={commitHash} onChange={(e) => setCommitHash(e.target.value)} /></Field>
                <Field label="commit tag"><input style={S.input} value={commitTag} onChange={(e) => setCommitTag(e.target.value)} /></Field>
                <Field label="정렬순서"><input type="number" style={S.input} value={sortOrder} onChange={(e) => setSortOrder(Number(e.target.value))} /></Field>
                <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "#555", marginTop: 20 }}>
                    <input type="checkbox" checked={isCurrent} onChange={(e) => setIsCurrent(e.target.checked)} />
                    현재 재직중
                </label>
            </div>
            <label style={S.label}>기술 스택 (쉼표로 구분)</label>
            <input style={S.input} value={stackText} onChange={(e) => setStackText(e.target.value)} />
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>회사 저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>회사 삭제</button>
            </div>

            <div style={{ marginTop: 16, paddingTop: 14, borderTop: "1px dashed #e3e6f0" }}>
                <h4 style={S.subSectionTitle}>프로젝트</h4>
                {company.projects.map((p) => (
                    <CareerProjectRow key={p.id} project={p} onChange={onChange} />
                ))}
                <CareerProjectForm companyId={company.id} nextSortOrder={company.projects.length} onChange={onChange} />
            </div>
        </div>
    );
}

function CareerCompanyForm({ nextSortOrder, onChange }: { nextSortOrder: number; onChange: () => void }) {
    const [companyName, setCompanyName] = useState("");
    const [path, setPath] = useState("");
    const [periodLabel, setPeriodLabel] = useState("");
    const [role, setRole] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!companyName.trim() || !path.trim()) return;
        setBusy(true);
        try {
            await addCareerCompany({
                path: path.trim(),
                companyName: companyName.trim(),
                periodLabel: periodLabel.trim(),
                role: role.trim(),
                isCurrent: false,
                commitHash: null,
                commitTag: null,
                stack: [],
                sortOrder: nextSortOrder,
            });
            setCompanyName("");
            setPath("");
            setPeriodLabel("");
            setRole("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <div style={S.grid2}>
                <Field label="path"><input style={S.input} value={path} onChange={(e) => setPath(e.target.value)} placeholder="~/career/new-company" /></Field>
                <Field label="회사명"><input style={S.input} value={companyName} onChange={(e) => setCompanyName(e.target.value)} /></Field>
                <Field label="기간"><input style={S.input} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} /></Field>
                <Field label="직책"><input style={S.input} value={role} onChange={(e) => setRole(e.target.value)} /></Field>
            </div>
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 회사 추가</button>
        </div>
    );
}

function CareerProjectRow({ project, onChange }: { project: CareerProject; onChange: () => void }) {
    const [title, setTitle] = useState(project.title);
    const [periodLabel, setPeriodLabel] = useState(project.periodLabel ?? "");
    const [paragraphsText, setParagraphsText] = useState(project.paragraphs.join("\n\n"));
    const [sortOrder, setSortOrder] = useState(project.sortOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updateCareerProject(project.id, {
                companyId: project.companyId,
                title,
                periodLabel: periodLabel.trim() || null,
                paragraphs: paragraphsText.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean),
                sortOrder,
            });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm(`"${project.title}" 프로젝트를 삭제할까요?`)) return;
        setBusy(true);
        try {
            await deleteCareerProject(project.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#fff" }}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, flex: 2 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="프로젝트 제목" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} placeholder="기간 (선택)" />
                <input
                    type="number"
                    style={{ ...S.input, marginBottom: 0, width: 70 }}
                    value={sortOrder}
                    onChange={(e) => setSortOrder(Number(e.target.value))}
                />
            </div>
            <textarea
                style={{ ...S.input, minHeight: 90, marginTop: 8 }}
                value={paragraphsText}
                onChange={(e) => setParagraphsText(e.target.value)}
                placeholder="설명 문단 (문단 사이는 빈 줄로 구분)"
            />
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>삭제</button>
            </div>
        </div>
    );
}

function CareerProjectForm({ companyId, nextSortOrder, onChange }: { companyId: number; nextSortOrder: number; onChange: () => void }) {
    const [title, setTitle] = useState("");
    const [periodLabel, setPeriodLabel] = useState("");
    const [paragraphsText, setParagraphsText] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!title.trim()) return;
        setBusy(true);
        try {
            await addCareerProject({
                companyId,
                title: title.trim(),
                periodLabel: periodLabel.trim() || null,
                paragraphs: paragraphsText.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean),
                sortOrder: nextSortOrder,
            });
            setTitle("");
            setPeriodLabel("");
            setParagraphsText("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, flex: 2 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="새 프로젝트 제목" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} placeholder="기간 (선택)" />
            </div>
            <textarea style={{ ...S.input, minHeight: 60 }} value={paragraphsText} onChange={(e) => setParagraphsText(e.target.value)} placeholder="설명 문단 (빈 줄로 구분)" />
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 프로젝트 추가</button>
        </div>
    );
}

// ── 학력 ─────────────────────────────────────────

function EducationEditor({ education, onChange }: { education: Education[]; onChange: () => void }) {
    return (
        <section style={S.card}>
            <h2 style={S.sectionTitle}>학력</h2>
            {education.map((e) => (
                <EducationRow key={e.id} edu={e} onChange={onChange} />
            ))}
            <EducationForm nextSortOrder={education.length} onChange={onChange} />
        </section>
    );
}

function EducationRow({ edu, onChange }: { edu: Education; onChange: () => void }) {
    const [periodLabel, setPeriodLabel] = useState(edu.periodLabel);
    const [school, setSchool] = useState(edu.school);
    const [major, setMajor] = useState(edu.major);
    const [sortOrder, setSortOrder] = useState(edu.displayOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updateEducation(edu.id, { periodLabel, school, major, sortOrder });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm(`"${edu.school}" 항목을 삭제할까요?`)) return;
        setBusy(true);
        try {
            await deleteEducation(edu.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={S.subCard}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} placeholder="기간" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={school} onChange={(e) => setSchool(e.target.value)} placeholder="학교" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={major} onChange={(e) => setMajor(e.target.value)} placeholder="전공" />
                <input
                    type="number"
                    style={{ ...S.input, marginBottom: 0, width: 70 }}
                    value={sortOrder}
                    onChange={(e) => setSortOrder(Number(e.target.value))}
                />
            </div>
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>삭제</button>
            </div>
        </div>
    );
}

function EducationForm({ nextSortOrder, onChange }: { nextSortOrder: number; onChange: () => void }) {
    const [periodLabel, setPeriodLabel] = useState("");
    const [school, setSchool] = useState("");
    const [major, setMajor] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!school.trim()) return;
        setBusy(true);
        try {
            await addEducation({ periodLabel: periodLabel.trim(), school: school.trim(), major: major.trim(), sortOrder: nextSortOrder });
            setPeriodLabel("");
            setSchool("");
            setMajor("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} placeholder="기간" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={school} onChange={(e) => setSchool(e.target.value)} placeholder="학교" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={major} onChange={(e) => setMajor(e.target.value)} placeholder="전공" />
            </div>
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 학력 추가</button>
        </div>
    );
}

// ── 공통 ─────────────────────────────────────────

function Field({ label, children }: { label: string; children: ReactNode }) {
    return (
        <div>
            <label style={S.label}>{label}</label>
            {children}
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 860, margin: "0 auto", padding: "40px 24px 100px" },
    kicker: { fontSize: 12, fontWeight: 700, letterSpacing: "0.14em" },
    title: { margin: "8px 0 0", fontSize: 30, fontWeight: 800, color: "#1b2236" },
    subtitle: { margin: "10px 0 0", fontSize: 15, color: "#8a90a3", lineHeight: 1.6 },
    card: {
        background: "#fff", border: "1px solid #eceef5", borderRadius: 16,
        padding: "22px 24px", marginBottom: 20, boxShadow: "0 6px 18px rgba(30,41,90,0.04)",
    },
    sectionTitle: { margin: "0 0 16px", fontSize: 19, fontWeight: 800, color: "#1b2236" },
    subSectionTitle: { margin: "0 0 10px", fontSize: 14, fontWeight: 700, color: "#4b5563" },
    subCard: {
        background: "#fafbfd", border: "1px solid #eceef5", borderRadius: 12,
        padding: 16, marginBottom: 12,
    },
    label: { display: "block", fontSize: 12, fontWeight: 600, color: "#8a90a3", margin: "10px 0 6px" },
    input: {
        width: "100%", boxSizing: "border-box", padding: "10px 12px", marginBottom: 10,
        borderRadius: 8, border: "1px solid #d8dbe6", fontSize: 14, fontFamily: "inherit",
        background: "#fff", color: "#1b2236", resize: "vertical",
    },
    row: { display: "flex", gap: 8, flexWrap: "wrap" },
    rowActions: { display: "flex", gap: 8, marginTop: 10 },
    grid2: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 14px" },
    saveBtn: {
        color: "#fff", border: "none", fontSize: 14, fontWeight: 700,
        padding: "10px 18px", borderRadius: 10, cursor: "pointer", marginTop: 4,
    },
    miniSaveBtn: {
        background: "#eef0fe", color: "#4f46e5", border: "none", fontSize: 13, fontWeight: 700,
        padding: "8px 14px", borderRadius: 8, cursor: "pointer",
    },
    miniDelBtn: {
        background: "#fff", border: "1px solid #f3c9c9", color: "#dc2626", fontSize: 13, fontWeight: 600,
        padding: "8px 14px", borderRadius: 8, cursor: "pointer",
    },
};
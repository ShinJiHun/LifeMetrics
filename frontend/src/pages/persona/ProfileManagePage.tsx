import { useEffect, useState } from "react";
import type { CSSProperties, ReactNode } from "react";
import { Link } from "react-router-dom";
import {
    addCareerCompany,
    addCareerProject,
    addCareerProjectTask,
    addEducation,
    addIntroSection,
    deleteCareerCompany,
    deleteCareerProject,
    deleteCareerProjectTask,
    deleteCareerTaskMedia,
    deleteEducation,
    deleteIntroSection,
    fetchProfile,
    updateCareerCompany,
    updateCareerProject,
    updateCareerProjectTask,
    updateContact,
    updateEducation,
    updateIntro,
    updateIntroSection,
    uploadCareerTaskMedia,
    addPersonalProject,
    updatePersonalProject,
    deletePersonalProject,
    addPersonalProjectFeature,
    updatePersonalProjectFeature,
    deletePersonalProjectFeature,
    addTroubleshoot,
    updateTroubleshoot,
    deleteTroubleshoot,
    addDependency,
    updateDependency,
    deleteDependency,
    type BasicProfile,
    type CareerCompany,
    type CareerProject,
    type CareerProjectTask,
    type Education,
    type IntroSection,
    type PersonalProject,
    type PersonalProjectFeature,
    type PortfolioTroubleshoot,
    type PortfolioDependency,
} from "@/api/profile";
import RichTextEditor from "@/components/common/RichTextEditor";

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
            <PersonalProjectsEditor
                projects={profile.personalProjects}
                troubleshoots={profile.troubleshoots}
                dependencies={profile.dependencies}
                onChange={reload}
            />
        </div>
    );
}

// ── 소개 ─────────────────────────────────────────

function IntroEditor({ intro, onChange }: { intro: BasicProfile["intro"]; onChange: () => void }) {
    const [elevatorPitch, setElevatorPitch] = useState(intro.elevatorPitch);
    const [highlightsText, setHighlightsText] = useState(intro.highlights.join("\n"));
    const [headline, setHeadline] = useState(intro.headline);
    const [subheadline, setSubheadline] = useState(intro.subheadline);
    const [roleTagline, setRoleTagline] = useState(intro.roleTagline);
    const [focusTagsText, setFocusTagsText] = useState(intro.focusTags.join(", "));
    const [contactBlurb, setContactBlurb] = useState(intro.contactBlurb);
    const [sideProject, setSideProject] = useState(intro.sideProject);
    const [availability, setAvailability] = useState(intro.availability);
    const [openToWork, setOpenToWork] = useState(intro.openToWork);
    const [jobSearchNote, setJobSearchNote] = useState(intro.jobSearchNote);
    const [saving, setSaving] = useState(false);

    const save = async () => {
        setSaving(true);
        try {
            await updateIntro({
                elevatorPitch,
                highlights: highlightsText.split("\n").map((l) => l.trim()).filter(Boolean),
                headline,
                subheadline,
                roleTagline,
                focusTags: focusTagsText.split(",").map((t) => t.trim()).filter(Boolean),
                contactBlurb,
                sideProject,
                availability,
                openToWork,
                jobSearchNote,
            });
            onChange();
        } finally {
            setSaving(false);
        }
    };

    return (
        <section style={S.card}>
            <h2 style={S.sectionTitle}>소개</h2>

            <label style={S.label}>
                포트폴리오 첫 화면 제목 (줄바꿈은 그대로 적용, 강조하고 싶은 부분은 <code>{"{{이렇게}}"}</code>로 감싸면 보라색으로 표시됩니다)
            </label>
            <textarea style={{ ...S.input, minHeight: 60 }} value={headline} onChange={(e) => setHeadline(e.target.value)} />

            <label style={S.label}>첫 화면 부제</label>
            <textarea style={{ ...S.input, minHeight: 60 }} value={subheadline} onChange={(e) => setSubheadline(e.target.value)} />

            <label style={S.label}>인용구 (elevator pitch)</label>
            <textarea style={{ ...S.input, minHeight: 60 }} value={elevatorPitch} onChange={(e) => setElevatorPitch(e.target.value)} />

            <label style={S.label}>핵심 성과 (한 줄에 하나씩)</label>
            <textarea style={{ ...S.input, minHeight: 90 }} value={highlightsText} onChange={(e) => setHighlightsText(e.target.value)} />

            <label style={S.label}>직함 태그라인 (whoami 카드 · 푸터에 표시)</label>
            <input style={S.input} value={roleTagline} onChange={(e) => setRoleTagline(e.target.value)} placeholder="예: Backend Developer" />

            <label style={S.label}>전문분야 태그 (쉼표로 구분)</label>
            <input style={S.input} value={focusTagsText} onChange={(e) => setFocusTagsText(e.target.value)} placeholder="예: STT, gRPC, MRCP" />

            <label style={S.label}>연락처 섹션 안내 문구</label>
            <textarea style={{ ...S.input, minHeight: 60 }} value={contactBlurb} onChange={(e) => setContactBlurb(e.target.value)} />

            <label style={S.label}>사이드 프로젝트 이름 (whoami 카드 sideProject)</label>
            <input style={S.input} value={sideProject} onChange={(e) => setSideProject(e.target.value)} placeholder="예: LifeMetrics" />

            <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "#555", marginTop: 12 }}>
                <input type="checkbox" checked={openToWork} onChange={(e) => setOpenToWork(e.target.checked)} />
                이직 / 구직 준비 중 (히어로에 초록 뱃지 표시)
            </label>

            <label style={S.label}>구직 상태 문구 (위 체크 시 뱃지에 표시)</label>
            <input style={S.input} value={availability} onChange={(e) => setAvailability(e.target.value)} placeholder="예: 이직 준비 중" />

            <label style={S.label}>현재 구직 상황 · 다음 계획 (페르소나 챗 전용, 포트폴리오에는 안 보임)</label>
            <textarea
                style={{ ...S.input, minHeight: 60 }}
                value={jobSearchNote}
                onChange={(e) => setJobSearchNote(e.target.value)}
                placeholder="예: 여러 기업 면접 단계, 음성인식 회사에 가장 관심..."
            />

            <p style={{ ...S.label, color: "#9CA1B5", marginTop: 4 }}>
                ※ 총 경력 연차 · 참여 프로젝트 수 · 재직 상태(재직 중 / 재직 중 아님)는 아래 <b>경력</b> 항목에서 자동 계산됩니다
                (재직중으로 체크된 회사가 있으면 "재직 중").
            </p>

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
    const [domain, setDomain] = useState(company.domain ?? "");
    const [companyName, setCompanyName] = useState(company.companyName);
    const [shortName, setShortName] = useState(company.shortName ?? "");
    const [periodLabel, setPeriodLabel] = useState(company.periodLabel);
    const [startDate, setStartDate] = useState(company.startDate ?? "");
    const [endDate, setEndDate] = useState(company.endDate ?? "");
    const [role, setRole] = useState(company.role);
    const [leaveReason, setLeaveReason] = useState(company.leaveReason ?? "");
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
                domain: domain.trim() || null,
                companyName,
                shortName: shortName.trim() || null,
                periodLabel,
                startDate: startDate || null,
                endDate: endDate || null,
                role,
                leaveReason: leaveReason.trim() || null,
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
                <Field label="도메인 (경력기술서 좌측 메뉴)"><input style={S.input} value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="예: 음성인식" /></Field>
                <Field label="회사명"><input style={S.input} value={companyName} onChange={(e) => setCompanyName(e.target.value)} /></Field>
                <Field label="짧은 표기명 (whoami 카드 등)"><input style={S.input} value={shortName} onChange={(e) => setShortName(e.target.value)} placeholder="예: TNS Soft" /></Field>
                <Field label="기간 표시 문구"><input style={S.input} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} placeholder="예: 2024.04 ~ 재직중 · 2년 5개월" /></Field>
                <Field label="직책"><input style={S.input} value={role} onChange={(e) => setRole(e.target.value)} /></Field>
                <Field label="입사일 (연차 계산용)"><input type="date" style={S.input} value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
                <Field label="퇴사일 (재직중이면 비움)"><input type="date" style={S.input} value={endDate} onChange={(e) => setEndDate(e.target.value)} /></Field>
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
            <label style={S.label}>퇴사/이직 사유 (페르소나 챗 전용, 포트폴리오에는 안 보임 · 현재 회사면 비움)</label>
            <textarea style={{ ...S.input, minHeight: 56 }} value={leaveReason} onChange={(e) => setLeaveReason(e.target.value)} />
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
    const [shortName, setShortName] = useState("");
    const [path, setPath] = useState("");
    const [domain, setDomain] = useState("");
    const [periodLabel, setPeriodLabel] = useState("");
    const [startDate, setStartDate] = useState("");
    const [endDate, setEndDate] = useState("");
    const [role, setRole] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!companyName.trim() || !path.trim()) return;
        setBusy(true);
        try {
            await addCareerCompany({
                path: path.trim(),
                domain: domain.trim() || null,
                companyName: companyName.trim(),
                shortName: shortName.trim() || null,
                periodLabel: periodLabel.trim(),
                startDate: startDate || null,
                endDate: endDate || null,
                role: role.trim(),
                leaveReason: null,
                isCurrent: false,
                commitHash: null,
                commitTag: null,
                stack: [],
                sortOrder: nextSortOrder,
            });
            setCompanyName("");
            setShortName("");
            setPath("");
            setDomain("");
            setPeriodLabel("");
            setStartDate("");
            setEndDate("");
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
                <Field label="도메인 (경력기술서 좌측 메뉴)"><input style={S.input} value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="예: 음성인식" /></Field>
                <Field label="회사명"><input style={S.input} value={companyName} onChange={(e) => setCompanyName(e.target.value)} /></Field>
                <Field label="짧은 표기명 (whoami 카드 등)"><input style={S.input} value={shortName} onChange={(e) => setShortName(e.target.value)} placeholder="예: TNS Soft" /></Field>
                <Field label="기간 표시 문구"><input style={S.input} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} placeholder="예: 2024.04 ~ 재직중 · 2년 5개월" /></Field>
                <Field label="직책"><input style={S.input} value={role} onChange={(e) => setRole(e.target.value)} /></Field>
                <Field label="입사일 (연차 계산용)"><input type="date" style={S.input} value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
                <Field label="퇴사일 (재직중이면 비움)"><input type="date" style={S.input} value={endDate} onChange={(e) => setEndDate(e.target.value)} /></Field>
            </div>
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 회사 추가</button>
        </div>
    );
}

function CareerProjectRow({ project, onChange }: { project: CareerProject; onChange: () => void }) {
    const [title, setTitle] = useState(project.title);
    const [periodLabel, setPeriodLabel] = useState(project.periodLabel ?? "");
    const [overview, setOverview] = useState(project.overview);
    const [sortOrder, setSortOrder] = useState(project.sortOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updateCareerProject(project.id, {
                companyId: project.companyId,
                title,
                periodLabel: periodLabel.trim() || null,
                overview,
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
            <div style={{ marginTop: 8 }}>
                <RichTextEditor value={overview} onChange={setOverview} accent={accent} placeholder="프로젝트 개요 — 블로그 글처럼 자유롭게 작성하세요" />
            </div>
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>삭제</button>
            </div>

            <div style={{ marginTop: 14, paddingTop: 12, borderTop: "1px dashed #e3e6f0" }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: "#8a90a3", marginBottom: 8 }}>업무 (개요/기간/화면 이미지·GIF·영상)</div>
                {project.tasks.map((t) => (
                    <CareerTaskRow key={t.id} task={t} onChange={onChange} />
                ))}
                <CareerTaskForm projectId={project.id} nextSortOrder={project.tasks.length} onChange={onChange} />
            </div>
        </div>
    );
}

function CareerTaskRow({ task, onChange }: { task: CareerProjectTask; onChange: () => void }) {
    const [description, setDescription] = useState(task.description);
    const [sortOrder, setSortOrder] = useState(task.sortOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updateCareerProjectTask(task.id, { projectId: task.projectId, description, sortOrder });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm("이 업무 항목을 삭제할까요? (첨부된 미디어도 함께 삭제됩니다)")) return;
        setBusy(true);
        try {
            await deleteCareerProjectTask(task.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const uploadFile = async (file: File) => {
        setBusy(true);
        try {
            await uploadCareerTaskMedia(task.id, file);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const removeMedia = async (mediaId: number) => {
        setBusy(true);
        try {
            await deleteCareerTaskMedia(mediaId);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#fff" }}>
            <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 4 }}>
                <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "#8a90a3" }}>
                    정렬순서
                    <input
                        type="number"
                        style={{ ...S.input, marginBottom: 0, width: 70 }}
                        value={sortOrder}
                        onChange={(e) => setSortOrder(Number(e.target.value))}
                    />
                </label>
            </div>
            <RichTextEditor value={description} onChange={setDescription} accent={accent} placeholder="업무 설명 — 블로그 글처럼 자유롭게 작성하세요" />

            {task.media.length > 0 && (
                <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 10 }}>
                    {task.media.map((m) => (
                        <div key={m.id} style={{ position: "relative" }}>
                            {m.mediaKind === "VIDEO" ? (
                                <video src={m.url} style={S.mediaThumb} controls muted />
                            ) : (
                                <img src={m.url} style={S.mediaThumb} alt="" />
                            )}
                            <button
                                type="button"
                                onClick={() => removeMedia(m.id)}
                                disabled={busy}
                                style={S.mediaDelBtn}
                                aria-label="미디어 삭제"
                            >
                                ×
                            </button>
                        </div>
                    ))}
                </div>
            )}

            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>저장</button>
                <label style={{ ...S.miniSaveBtn, background: "#eef7ee", color: "#1a7f37", cursor: "pointer" }}>
                    + 이미지/GIF/영상 업로드
                    <input
                        type="file"
                        accept="image/*,video/*"
                        style={{ display: "none" }}
                        disabled={busy}
                        onChange={(e) => {
                            const file = e.target.files?.[0];
                            if (file) uploadFile(file);
                            e.target.value = "";
                        }}
                    />
                </label>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>업무 삭제</button>
            </div>
        </div>
    );
}

function CareerTaskForm({ projectId, nextSortOrder, onChange }: { projectId: number; nextSortOrder: number; onChange: () => void }) {
    const [description, setDescription] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!description.trim()) return;
        setBusy(true);
        try {
            await addCareerProjectTask({ projectId, description: description.trim(), sortOrder: nextSortOrder });
            setDescription("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <RichTextEditor value={description} onChange={setDescription} accent={accent} placeholder="새 업무 설명 — 블로그 글처럼 자유롭게 작성하세요" />
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 업무 추가</button>
        </div>
    );
}

function CareerProjectForm({ companyId, nextSortOrder, onChange }: { companyId: number; nextSortOrder: number; onChange: () => void }) {
    const [title, setTitle] = useState("");
    const [periodLabel, setPeriodLabel] = useState("");
    const [overview, setOverview] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!title.trim()) return;
        setBusy(true);
        try {
            await addCareerProject({
                companyId,
                title: title.trim(),
                periodLabel: periodLabel.trim() || null,
                overview,
                sortOrder: nextSortOrder,
            });
            setTitle("");
            setPeriodLabel("");
            setOverview("");
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
            <RichTextEditor value={overview} onChange={setOverview} accent={accent} placeholder="프로젝트 개요 — 블로그 글처럼 자유롭게 작성하세요" />
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

// ── 개인 프로젝트 (05 섹션) ─────────────────────────

function PersonalProjectsEditor({
    projects,
    troubleshoots,
    dependencies,
    onChange,
}: {
    projects: PersonalProject[];
    troubleshoots: PortfolioTroubleshoot[];
    dependencies: PortfolioDependency[];
    onChange: () => void;
}) {
    return (
        <section style={S.card}>
            <h2 style={S.sectionTitle}>개인 프로젝트 (05 섹션)</h2>
            <p style={{ ...S.label, color: "#9CA1B5", marginTop: 0 }}>
                아키텍처 다이어그램은 코드에 고정돼 있고, 나머지(대표 프로젝트·기능 카드·트러블슈팅·기술 선택 이유·작은 프로젝트)는 여기서 관리합니다.
            </p>

            <h3 style={S.subSectionTitle}>프로젝트 (FEATURED = 대표 · 기능 카드 포함 / MINI = 작은 카드)</h3>
            {projects.map((p) => (
                <PersonalProjectCard key={p.id} project={p} onChange={onChange} />
            ))}
            <PersonalProjectForm nextSortOrder={projects.length} onChange={onChange} />

            <div style={{ marginTop: 20 }}>
                <h3 style={S.subSectionTitle}>트러블슈팅 로그</h3>
                {troubleshoots.map((t) => (
                    <TroubleshootRow key={t.id} ts={t} onChange={onChange} />
                ))}
                <TroubleshootForm nextSortOrder={troubleshoots.length} onChange={onChange} />
            </div>

            <div style={{ marginTop: 20 }}>
                <h3 style={S.subSectionTitle}>기술 선택 이유 (카테고리별)</h3>
                {dependencies.map((d) => (
                    <DependencyRow key={d.id} dep={d} onChange={onChange} />
                ))}
                <DependencyForm nextSortOrder={dependencies.length} onChange={onChange} />
            </div>
        </section>
    );
}

function PersonalProjectCard({ project, onChange }: { project: PersonalProject; onChange: () => void }) {
    const [kind, setKind] = useState<PersonalProject["kind"]>(project.kind);
    const [title, setTitle] = useState(project.title);
    const [blurb, setBlurb] = useState(project.blurb);
    const [repoUrl, setRepoUrl] = useState(project.repoUrl ?? "");
    const [periodLabel, setPeriodLabel] = useState(project.periodLabel ?? "");
    const [tagsText, setTagsText] = useState(project.tags.join(", "));
    const [sortOrder, setSortOrder] = useState(project.sortOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updatePersonalProject(project.id, {
                kind,
                title,
                blurb,
                repoUrl: repoUrl.trim() || null,
                periodLabel: periodLabel.trim() || null,
                tags: tagsText.split(",").map((t) => t.trim()).filter(Boolean),
                sortOrder,
            });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm(`"${project.title}" 프로젝트를 삭제할까요? (기능 카드도 함께 삭제됩니다)`)) return;
        setBusy(true);
        try {
            await deletePersonalProject(project.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={S.subCard}>
            <div style={S.grid2}>
                <Field label="종류">
                    <select style={S.input} value={kind} onChange={(e) => setKind(e.target.value as PersonalProject["kind"])}>
                        <option value="FEATURED">FEATURED (대표)</option>
                        <option value="MINI">MINI (작은 카드)</option>
                    </select>
                </Field>
                <Field label="제목"><input style={S.input} value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
                <Field label="GitHub URL (비우면 연락처 GitHub 사용)"><input style={S.input} value={repoUrl} onChange={(e) => setRepoUrl(e.target.value)} placeholder="https://github.com/..." /></Field>
                <Field label="기간 문구 (MINI 카드용)"><input style={S.input} value={periodLabel} onChange={(e) => setPeriodLabel(e.target.value)} placeholder="예: 대학원 논문 주제 · 2016 ~ 2018" /></Field>
                <Field label="태그 (쉼표 구분, MINI 카드용)"><input style={S.input} value={tagsText} onChange={(e) => setTagsText(e.target.value)} placeholder="Java, Tomcat, MySQL" /></Field>
                <Field label="정렬순서"><input type="number" style={S.input} value={sortOrder} onChange={(e) => setSortOrder(Number(e.target.value))} /></Field>
            </div>
            <label style={S.label}>설명</label>
            <textarea style={{ ...S.input, minHeight: 70 }} value={blurb} onChange={(e) => setBlurb(e.target.value)} />
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>프로젝트 저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>프로젝트 삭제</button>
            </div>

            {kind === "FEATURED" && (
                <div style={{ marginTop: 16, paddingTop: 14, borderTop: "1px dashed #e3e6f0" }}>
                    <h4 style={S.subSectionTitle}>기능 카드</h4>
                    {project.features.map((f) => (
                        <FeatureRow key={f.id} feature={f} onChange={onChange} />
                    ))}
                    <FeatureForm projectId={project.id} nextSortOrder={project.features.length} onChange={onChange} />
                </div>
            )}
        </div>
    );
}

function PersonalProjectForm({ nextSortOrder, onChange }: { nextSortOrder: number; onChange: () => void }) {
    const [kind, setKind] = useState<PersonalProject["kind"]>("MINI");
    const [title, setTitle] = useState("");
    const [blurb, setBlurb] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!title.trim()) return;
        setBusy(true);
        try {
            await addPersonalProject({ kind, title: title.trim(), blurb, repoUrl: null, periodLabel: null, tags: [], sortOrder: nextSortOrder });
            setTitle("");
            setBlurb("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <div style={S.row}>
                <select style={{ ...S.input, marginBottom: 0, width: 160 }} value={kind} onChange={(e) => setKind(e.target.value as PersonalProject["kind"])}>
                    <option value="FEATURED">FEATURED</option>
                    <option value="MINI">MINI</option>
                </select>
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="프로젝트 제목" />
            </div>
            <textarea style={{ ...S.input, minHeight: 50, marginTop: 8 }} value={blurb} onChange={(e) => setBlurb(e.target.value)} placeholder="설명" />
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 프로젝트 추가</button>
        </div>
    );
}

function FeatureRow({ feature, onChange }: { feature: PersonalProjectFeature; onChange: () => void }) {
    const [icon, setIcon] = useState(feature.icon ?? "");
    const [title, setTitle] = useState(feature.title);
    const [description, setDescription] = useState(feature.description);
    const [tagsText, setTagsText] = useState(feature.tags.join(", "));
    const [sortOrder, setSortOrder] = useState(feature.sortOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updatePersonalProjectFeature(feature.id, {
                projectId: feature.projectId,
                icon: icon.trim() || null,
                title,
                description,
                tags: tagsText.split(",").map((t) => t.trim()).filter(Boolean),
                sortOrder,
            });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm(`"${feature.title}" 기능 카드를 삭제할까요?`)) return;
        setBusy(true);
        try {
            await deletePersonalProjectFeature(feature.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={S.subCard}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, width: 60 }} value={icon} onChange={(e) => setIcon(e.target.value)} placeholder="📐" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="기능 제목" />
                <input type="number" style={{ ...S.input, marginBottom: 0, width: 64 }} value={sortOrder} onChange={(e) => setSortOrder(Number(e.target.value))} />
            </div>
            <textarea style={{ ...S.input, minHeight: 56, marginTop: 8 }} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="설명" />
            <input style={S.input} value={tagsText} onChange={(e) => setTagsText(e.target.value)} placeholder="태그 (쉼표 구분)" />
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>삭제</button>
            </div>
        </div>
    );
}

function FeatureForm({ projectId, nextSortOrder, onChange }: { projectId: number; nextSortOrder: number; onChange: () => void }) {
    const [icon, setIcon] = useState("");
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [tagsText, setTagsText] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!title.trim()) return;
        setBusy(true);
        try {
            await addPersonalProjectFeature({
                projectId,
                icon: icon.trim() || null,
                title: title.trim(),
                description,
                tags: tagsText.split(",").map((t) => t.trim()).filter(Boolean),
                sortOrder: nextSortOrder,
            });
            setIcon("");
            setTitle("");
            setDescription("");
            setTagsText("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, width: 60 }} value={icon} onChange={(e) => setIcon(e.target.value)} placeholder="📐" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="기능 제목" />
            </div>
            <textarea style={{ ...S.input, minHeight: 50, marginTop: 8 }} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="설명" />
            <input style={S.input} value={tagsText} onChange={(e) => setTagsText(e.target.value)} placeholder="태그 (쉼표 구분)" />
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 기능 카드 추가</button>
        </div>
    );
}

function TroubleshootRow({ ts, onChange }: { ts: PortfolioTroubleshoot; onChange: () => void }) {
    const [refLabel, setRefLabel] = useState(ts.refLabel ?? "");
    const [title, setTitle] = useState(ts.title);
    const [removedText, setRemovedText] = useState(ts.removed.join("\n"));
    const [addedText, setAddedText] = useState(ts.added.join("\n"));
    const [sortOrder, setSortOrder] = useState(ts.sortOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updateTroubleshoot(ts.id, {
                refLabel: refLabel.trim() || null,
                title,
                removed: removedText.split("\n").map((l) => l.trim()).filter(Boolean),
                added: addedText.split("\n").map((l) => l.trim()).filter(Boolean),
                sortOrder,
            });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm(`"${ts.title}" 항목을 삭제할까요?`)) return;
        setBusy(true);
        try {
            await deleteTroubleshoot(ts.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={S.subCard}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, width: 90 }} value={refLabel} onChange={(e) => setRefLabel(e.target.value)} placeholder="#42" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목" />
                <input type="number" style={{ ...S.input, marginBottom: 0, width: 64 }} value={sortOrder} onChange={(e) => setSortOrder(Number(e.target.value))} />
            </div>
            <label style={S.label}>증상 / 원인 (− 라인, 한 줄에 하나씩)</label>
            <textarea style={{ ...S.input, minHeight: 56 }} value={removedText} onChange={(e) => setRemovedText(e.target.value)} />
            <label style={S.label}>조치 / 교훈 (+ 라인, 한 줄에 하나씩)</label>
            <textarea style={{ ...S.input, minHeight: 56 }} value={addedText} onChange={(e) => setAddedText(e.target.value)} />
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>삭제</button>
            </div>
        </div>
    );
}

function TroubleshootForm({ nextSortOrder, onChange }: { nextSortOrder: number; onChange: () => void }) {
    const [refLabel, setRefLabel] = useState("");
    const [title, setTitle] = useState("");
    const [removedText, setRemovedText] = useState("");
    const [addedText, setAddedText] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!title.trim()) return;
        setBusy(true);
        try {
            await addTroubleshoot({
                refLabel: refLabel.trim() || null,
                title: title.trim(),
                removed: removedText.split("\n").map((l) => l.trim()).filter(Boolean),
                added: addedText.split("\n").map((l) => l.trim()).filter(Boolean),
                sortOrder: nextSortOrder,
            });
            setRefLabel("");
            setTitle("");
            setRemovedText("");
            setAddedText("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, width: 90 }} value={refLabel} onChange={(e) => setRefLabel(e.target.value)} placeholder="#42" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목" />
            </div>
            <textarea style={{ ...S.input, minHeight: 50, marginTop: 8 }} value={removedText} onChange={(e) => setRemovedText(e.target.value)} placeholder="증상 / 원인 (한 줄에 하나씩)" />
            <textarea style={{ ...S.input, minHeight: 50 }} value={addedText} onChange={(e) => setAddedText(e.target.value)} placeholder="조치 / 교훈 (한 줄에 하나씩)" />
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 트러블슈팅 추가</button>
        </div>
    );
}

function DependencyRow({ dep, onChange }: { dep: PortfolioDependency; onChange: () => void }) {
    const [category, setCategory] = useState(dep.category);
    const [depKey, setDepKey] = useState(dep.depKey);
    const [note, setNote] = useState(dep.note);
    const [sortOrder, setSortOrder] = useState(dep.sortOrder);
    const [busy, setBusy] = useState(false);

    const save = async () => {
        setBusy(true);
        try {
            await updateDependency(dep.id, { category: category.trim(), depKey: depKey.trim(), note, sortOrder });
            onChange();
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        if (!confirm(`"${dep.depKey}" 항목을 삭제할까요?`)) return;
        setBusy(true);
        try {
            await deleteDependency(dep.id);
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={S.subCard}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={category} onChange={(e) => setCategory(e.target.value)} placeholder="카테고리 (예: infra)" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={depKey} onChange={(e) => setDepKey(e.target.value)} placeholder="키 (예: open-meteo)" />
                <input type="number" style={{ ...S.input, marginBottom: 0, width: 64 }} value={sortOrder} onChange={(e) => setSortOrder(Number(e.target.value))} />
            </div>
            <input style={S.input} value={note} onChange={(e) => setNote(e.target.value)} placeholder="선택 이유" />
            <div style={S.rowActions}>
                <button type="button" onClick={save} disabled={busy} style={S.miniSaveBtn}>저장</button>
                <button type="button" onClick={remove} disabled={busy} style={S.miniDelBtn}>삭제</button>
            </div>
        </div>
    );
}

function DependencyForm({ nextSortOrder, onChange }: { nextSortOrder: number; onChange: () => void }) {
    const [category, setCategory] = useState("");
    const [depKey, setDepKey] = useState("");
    const [note, setNote] = useState("");
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        if (!category.trim() || !depKey.trim()) return;
        setBusy(true);
        try {
            await addDependency({ category: category.trim(), depKey: depKey.trim(), note: note.trim(), sortOrder: nextSortOrder });
            setCategory("");
            setDepKey("");
            setNote("");
            onChange();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ ...S.subCard, background: "#f8f9fc" }}>
            <div style={S.row}>
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={category} onChange={(e) => setCategory(e.target.value)} placeholder="카테고리" />
                <input style={{ ...S.input, marginBottom: 0, flex: 1 }} value={depKey} onChange={(e) => setDepKey(e.target.value)} placeholder="키" />
            </div>
            <input style={{ ...S.input, marginTop: 8 }} value={note} onChange={(e) => setNote(e.target.value)} placeholder="선택 이유" />
            <button type="button" onClick={submit} disabled={busy} style={{ ...S.saveBtn, background: accent }}>+ 항목 추가</button>
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
    mediaThumb: {
        width: 120, height: 80, objectFit: "cover", borderRadius: 6, border: "1px solid #e3e6f0", background: "#000",
    },
    mediaDelBtn: {
        position: "absolute", top: -6, right: -6, width: 20, height: 20, borderRadius: "50%",
        background: "#dc2626", color: "#fff", border: "none", fontSize: 13, lineHeight: "20px",
        padding: 0, cursor: "pointer",
    },
};
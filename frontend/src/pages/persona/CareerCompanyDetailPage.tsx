import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { PERSONA_META } from "@/lib/persona";
import { fetchProfile } from "@/api/profile";
import type { BasicProfile, CareerProjectTask } from "@/api/profile";
import RichText from "@/components/common/RichText";

const accent = PERSONA_META.developer.accent;

export default function CareerCompanyDetailPage() {
    const { companyId, page: pageParam } = useParams<{ companyId: string; page?: string }>();
    const navigate = useNavigate();
    const [profile, setProfile] = useState<BasicProfile | null>(null);

    useEffect(() => {
        fetchProfile().then(setProfile).catch(() => {});
    }, []);

    const company = useMemo(() => {
        if (!profile) return null;
        return profile.career.find((c) => c.id === Number(companyId)) ?? null;
    }, [profile, companyId]);

    const projects = useMemo(() => {
        if (!company) return [];
        return [...company.projects].sort((a, b) => a.sortOrder - b.sortOrder);
    }, [company]);

    const page = Math.min(Math.max(Number(pageParam) || 1, 1), Math.max(projects.length, 1));
    const project = projects[page - 1];

    const goToPage = (n: number) => {
        navigate(`/persona/developer/career-detail/${companyId}/${n}`);
    };

    if (!profile) {
        return <div style={S.page}>불러오는 중...</div>;
    }

    if (!company) {
        return (
            <div style={S.page}>
                <style>{CSS}</style>
                <p style={{ color: "#6B7280" }}>회사를 찾을 수 없습니다.</p>
                <Link to="/persona/developer/career-detail" className="cd-back">← 경력 기술서로</Link>
            </div>
        );
    }

    return (
        <div style={S.page}>
            <style>{CSS}</style>

            <Link to="/persona/developer/career-detail" className="cd-back">← 경력 기술서로</Link>

            <div style={{ marginTop: 18, marginBottom: 34 }}>
                <div style={{ display: "flex", alignItems: "baseline", gap: 12, flexWrap: "wrap" }}>
                    <h1 style={{ fontWeight: 800, fontSize: 26, margin: 0 }}>{company.companyName}</h1>
                    <span className="cd-hash">{company.periodLabel}</span>
                </div>
                <div style={{ fontSize: 13.5, color: "#6B7280", marginTop: 6 }}>{company.role}</div>
                {company.stack.length > 0 && (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 14 }}>
                        {company.stack.map((s) => (
                            <span key={s} className="cd-tag">{s}</span>
                        ))}
                    </div>
                )}
            </div>

            {!project ? (
                <p style={{ color: "#6B7280" }}>등록된 프로젝트가 없습니다.</p>
            ) : (
                <>
                    <div style={{ fontFamily: "var(--pp-mono)", fontSize: 11.5, color: "#9CA1B5", marginBottom: 10 }}>
                        프로젝트 {page} / {projects.length}
                    </div>

                    <article className="cd-card">
                        <div style={{ display: "flex", alignItems: "baseline", gap: 12, flexWrap: "wrap", marginBottom: 6 }}>
                            <h2 style={{ fontWeight: 800, fontSize: 20, margin: 0 }}>{project.title}</h2>
                            {project.periodLabel && <span className="cd-hash">{project.periodLabel}</span>}
                        </div>

                        {project.overview.trim() && (
                            <section style={{ marginTop: 20 }}>
                                <SecLabel text="프로젝트 개요" />
                                <RichText>{project.overview}</RichText>
                            </section>
                        )}

                        {project.tasks.length > 0 && (
                            <section style={{ marginTop: 28 }}>
                                <SecLabel text="업무" />
                                <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
                                    {[...project.tasks]
                                        .sort((a, b) => a.sortOrder - b.sortOrder)
                                        .map((t) => (
                                            <TaskItem key={t.id} task={t} />
                                        ))}
                                </div>
                            </section>
                        )}
                    </article>

                    {projects.length > 1 && (
                        <nav className="cd-pagination" aria-label="프로젝트 페이지">
                            <button
                                type="button"
                                className="cd-page-btn"
                                onClick={() => goToPage(page - 1)}
                                disabled={page === 1}
                                aria-label="이전 프로젝트"
                            >
                                ←
                            </button>
                            {projects.map((_, i) => (
                                <button
                                    key={i}
                                    type="button"
                                    className={`cd-page-btn ${page === i + 1 ? "active" : ""}`}
                                    onClick={() => goToPage(i + 1)}
                                    aria-current={page === i + 1}
                                >
                                    {i + 1}
                                </button>
                            ))}
                            <button
                                type="button"
                                className="cd-page-btn"
                                onClick={() => goToPage(page + 1)}
                                disabled={page === projects.length}
                                aria-label="다음 프로젝트"
                            >
                                →
                            </button>
                        </nav>
                    )}
                </>
            )}
        </div>
    );
}

function SecLabel({ text }: { text: string }) {
    return <div style={{ fontFamily: "var(--pp-mono)", fontSize: 12, color: accent, fontWeight: 700, marginBottom: 12 }}>{text}</div>;
}

function TaskItem({ task }: { task: CareerProjectTask }) {
    const media = [...task.media].sort((a, b) => a.sortOrder - b.sortOrder);
    return (
        <div className="cd-task">
            <div style={{ marginBottom: media.length > 0 ? 12 : 0 }}>
                <RichText>{task.description}</RichText>
            </div>
            {media.length > 0 && (
                <div className="cd-media-grid">
                    {media.map((m) =>
                        m.mediaKind === "VIDEO" ? (
                            <video key={m.id} className="cd-media" src={m.url} controls playsInline />
                        ) : (
                            <img key={m.id} className="cd-media" src={m.url} loading="lazy" alt="" />
                        )
                    )}
                </div>
            )}
        </div>
    );
}

const S = {
    page: { maxWidth: 900, margin: "0 auto", padding: "32px 0 80px", fontFamily: "var(--pp-body, inherit)" } as const,
};

const CSS = `
  :root{ --pp-mono: 'JetBrains Mono', monospace; }

  .cd-back{ font-family: var(--pp-mono); font-size:13px; color:#6B7280; text-decoration:none; }
  .cd-back:hover{ color:${accent}; }

  .cd-hash{ font-family: var(--pp-mono); font-size:11.5px; color:#9CA1B5; }

  .cd-tag{ font-family: var(--pp-mono); font-size:10.5px; color:#6B7280; border:1px solid #E3E6F0; padding:3px 9px; border-radius:12px; background:#F0F1F8; }

  .cd-card{ background:#fff; border:1px solid #E3E6F0; border-radius:10px; padding:26px 28px; margin-bottom:24px; }

  .cd-task{ background:#FAFBFD; border:1px solid #ECEEF6; border-radius:8px; padding:16px 18px; }

  .cd-media-grid{ display:grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap:10px; }
  .cd-media{ width:100%; border-radius:6px; border:1px solid #E3E6F0; background:#000; display:block; max-height:280px; object-fit:contain; }

  .cd-pagination{ display:flex; align-items:center; justify-content:center; gap:6px; margin-top:8px; }
  .cd-page-btn{ min-width:34px; height:34px; padding:0 8px; border-radius:7px; border:1px solid #E3E6F0; background:#fff; color:#6B7280; font-family: var(--pp-mono); font-size:13px; cursor:pointer; transition: all .15s; }
  .cd-page-btn:hover:not(:disabled){ border-color:${accent}; color:${accent}; }
  .cd-page-btn.active{ background:${accent}; border-color:${accent}; color:#fff; font-weight:700; }
  .cd-page-btn:disabled{ opacity:0.35; cursor:default; }

  @media (max-width: 640px){
    .cd-card{ padding:20px; }
  }
`;

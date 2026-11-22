// 문서 뷰어 (관리자 전용) — 저장소 docs/ 의 기획서·결과서·작업 기록을 보여준다.
// 원본은 마크다운 파일이고 빌드 때 번들된다. 고치는 방법: 개발 요청함에 요청 → 워커가 .claude/skills/lm-docs 규칙대로 수정 → dev 배포.
// 새 문서를 추가하면 DOCS 에 slug 와 제목을 넣는다. docs/history/ 의 YYYY-MM-DD.md 는 자동으로 "작업 기록"에 붙는다.
import { lazy, Suspense, useMemo } from "react";
import type { ReactNode } from "react";
import { Link, NavLink, useParams } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "@/styles/markdown.css";
import "@/styles/docs.css";

// 구조·ERD 는 mermaid 를 쓰므로 이 탭을 열 때만 불러온다
const SchemaErd = lazy(() => import("./docs/SchemaErd"));

const files = import.meta.glob<string>("../../../docs/**/*.md", { query: "?raw", import: "default", eager: true });

const DOCS: { slug: string; title: string; path: string }[] = [
    { slug: "plan", title: "기획서", path: "../../../docs/plan.md" },
    { slug: "report", title: "결과서", path: "../../../docs/report.md" },
];

const HISTORY = Object.keys(files)
    .map((p) => p.match(/docs\/history\/(\d{4}-\d{2}-\d{2})\.md$/)?.[1])
    .filter((d): d is string => !!d)
    .sort()
    .reverse();

function DocLink({ href, children }: { href?: string; children?: ReactNode }) {
    if (href?.startsWith("/")) return <Link to={href}>{children}</Link>;
    return (
        <a href={href} target="_blank" rel="noreferrer">
            {children}
        </a>
    );
}

export default function DocsPage() {
    const { slug = "plan", date } = useParams();
    const isHistory = slug === "history";
    const isErd = slug === "erd";
    const historyDate = isHistory ? (date ?? HISTORY[0]) : null;

    const { md, label } = useMemo(() => {
        if (isHistory) {
            const p = historyDate ? `../../../docs/history/${historyDate}.md` : "";
            return { md: files[p] ?? "작업 기록이 없습니다.", label: `작업 기록 ${historyDate ?? ""}` };
        }
        const d = DOCS.find((x) => x.slug === slug) ?? DOCS[0];
        return { md: files[d.path] ?? "문서를 찾을 수 없습니다.", label: d.title };
    }, [slug, isHistory, historyDate]);

    const requestHref = `/dev-requests?draft=${encodeURIComponent(`[문서] ${label} 수정 요청 (lm-docs 스킬 사용)\n\n`)}`;

    return (
        <div className={`docs-page${isErd ? " docs-page--wide" : ""}`}>
            <header className="docs-head">
                <nav className="docs-tabs">
                    {DOCS.map((d) => (
                        <NavLink key={d.slug} to={`/docs/${d.slug}`} className={({ isActive }) => (isActive ? "on" : "")}>
                            {d.title}
                        </NavLink>
                    ))}
                    <NavLink to="/docs/history" className={isHistory ? "on" : ""}>
                        작업 기록
                    </NavLink>
                    <NavLink to="/docs/erd" className={isErd ? "on" : ""}>
                        구조·ERD
                    </NavLink>
                </nav>
                {!isErd && (
                    <div className="docs-actions">
                        <Link to={requestHref} className="docs-request">
                            수정 요청
                        </Link>
                    </div>
                )}
            </header>

            {isErd && (
                <Suspense fallback={<div className="erd-msg">불러오는 중…</div>}>
                    <SchemaErd />
                </Suspense>
            )}

            {isHistory && HISTORY.length > 0 && (
                <div className="docs-dates">
                    {HISTORY.map((d) => (
                        <NavLink key={d} to={`/docs/history/${d}`} className={d === historyDate ? "on" : ""}>
                            {d}
                        </NavLink>
                    ))}
                </div>
            )}

            {!isErd && (
                <article className="md-body docs-body">
                    <ReactMarkdown remarkPlugins={[remarkGfm]} components={{ a: ({ href, children }) => <DocLink href={href}>{children}</DocLink> }}>
                        {md}
                    </ReactMarkdown>
                </article>
            )}
        </div>
    );
}

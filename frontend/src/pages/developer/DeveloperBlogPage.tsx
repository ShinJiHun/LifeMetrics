import type { CSSProperties } from "react";

// ── 개발 블로그 예시 데이터 ───────────────────────────
interface Post {
    id: number;
    title: string;
    summary: string;
    tags: string[];
    date: string;
    readMin: number;
}

const POSTS: Post[] = [
    {
        id: 1,
        title: "LifeMetrics 페르소나 게이트 설계기",
        summary:
            "React Router로 하나의 앱에 세 개의 페르소나(개발/라이더/휴먼)를 분기하는 구조를 잡았다. 풀스크린 게이트와 사이드바 레이아웃을 어떻게 분리했는지 정리한다.",
        tags: ["React", "Router", "UX"],
        date: "2026-06-20",
        readMin: 6,
    },
    {
        id: 2,
        title: "FIT 파일 파싱과 라이딩 지도 렌더링",
        summary:
            "자전거 액티비티의 FIT/GPX 데이터를 파싱해 경로를 지도에 그리고, 속도·고도·심박을 시각화하기까지의 파이프라인.",
        tags: ["TypeScript", "Geo", "Visualization"],
        date: "2026-06-12",
        readMin: 9,
    },
    {
        id: 3,
        title: "블로그 기반 페르소나 챗봇 만들기",
        summary:
            "내 블로그 글을 크롤링·임베딩해서 '나'에 대해 답하는 챗봇을 붙였다. 프로필 자동 생성과 TTS까지의 과정을 기록.",
        tags: ["LLM", "RAG", "Chatbot"],
        date: "2026-06-01",
        readMin: 7,
    },
];

export default function DeveloperBlogPage() {
    return (
        <div style={S.page}>
            <header style={S.hero}>
                <div style={S.kicker}>DEVELOPER</div>
                <h1 style={S.title}>개발자 신지훈</h1>
                <p style={S.subtitle}>
                    코드, 프로젝트, 그리고 만들면서 배운 것들을 기록합니다.
                </p>
            </header>

            <section style={S.list}>
                {POSTS.map((post) => (
                    <article key={post.id} style={S.card}>
                        <div style={S.cardMeta}>
                            <span>{post.date}</span>
                            <span style={S.dot}>·</span>
                            <span>{post.readMin}분 읽기</span>
                        </div>
                        <h2 style={S.cardTitle}>{post.title}</h2>
                        <p style={S.cardSummary}>{post.summary}</p>
                        <div style={S.tags}>
                            {post.tags.map((t) => (
                                <span key={t} style={S.tag}>
                                    #{t}
                                </span>
                            ))}
                        </div>
                    </article>
                ))}
            </section>
        </div>
    );
}

const ACCENT = "#6366f1";

const S: Record<string, CSSProperties> = {
    page: {
        maxWidth: 760,
        margin: "0 auto",
        padding: "40px 24px 64px",
    },
    hero: {
        marginBottom: 40,
    },
    kicker: {
        fontSize: 13,
        fontWeight: 700,
        letterSpacing: "0.14em",
        color: ACCENT,
    },
    title: {
        margin: "10px 0 0",
        fontSize: 36,
        fontWeight: 800,
        letterSpacing: "-0.02em",
        color: "#1b2236",
    },
    subtitle: {
        margin: "12px 0 0",
        fontSize: 16,
        color: "#8a90a3",
        lineHeight: 1.6,
    },
    list: {
        display: "flex",
        flexDirection: "column",
        gap: 20,
    },
    card: {
        background: "#ffffff",
        borderRadius: 16,
        border: "1px solid #eceef5",
        padding: "24px 26px",
        boxShadow: "0 8px 24px rgba(30, 41, 90, 0.05)",
    },
    cardMeta: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 13,
        color: "#9aa0b2",
    },
    dot: { color: "#c7cbd9" },
    cardTitle: {
        margin: "10px 0 0",
        fontSize: 21,
        fontWeight: 700,
        color: "#1b2236",
        letterSpacing: "-0.01em",
    },
    cardSummary: {
        margin: "12px 0 0",
        fontSize: 15,
        lineHeight: 1.7,
        color: "#5a6072",
    },
    tags: {
        display: "flex",
        flexWrap: "wrap",
        gap: 8,
        marginTop: 18,
    },
    tag: {
        fontSize: 13,
        fontWeight: 600,
        color: ACCENT,
        background: "#eef0fe",
        borderRadius: 999,
        padding: "5px 12px",
    },
};

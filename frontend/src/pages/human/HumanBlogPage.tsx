import type { CSSProperties } from "react";

// ── 인간(일상) 블로그 예시 데이터 ─────────────────────
interface Post {
    id: number;
    title: string;
    summary: string;
    mood: string;
    date: string;
}

const POSTS: Post[] = [
    {
        id: 1,
        title: "새벽 라이딩이 가르쳐 준 것",
        summary:
            "해 뜨기 전 한강을 달리다 보면 머릿속이 조용해진다. 빠르게 가는 것보다 꾸준히 가는 게 더 어렵다는 걸 페달을 밟으며 배운다.",
        mood: "🌅 고요함",
        date: "2026-06-18",
    },
    {
        id: 2,
        title: "기록한다는 것에 대하여",
        summary:
            "몸무게, 운동, 글, 생각까지 전부 남겨두는 습관. 결국 나를 이해하려는 시도라는 걸 요즘 들어 느낀다.",
        mood: "📝 사색",
        date: "2026-06-09",
    },
    {
        id: 3,
        title: "잘 쉬는 법을 연습 중",
        summary:
            "아무것도 안 하는 시간을 죄책감 없이 보내는 게 생각보다 어렵다. 잘 쉬는 것도 잘 사는 일의 일부라고 믿어보기로 했다.",
        mood: "🌱 회복",
        date: "2026-05-30",
    },
];

export default function HumanBlogPage() {
    return (
        <div style={S.page}>
            <header style={S.hero}>
                <div style={S.kicker}>HUMAN</div>
                <h1 style={S.title}>인간 신지훈</h1>
                <p style={S.subtitle}>
                    일상에서 마주친 생각과 감정, 그리고 사람에 대한 기록.
                </p>
            </header>

            <section style={S.list}>
                {POSTS.map((post) => (
                    <article key={post.id} style={S.card}>
                        <div style={S.cardMeta}>
                            <span style={S.mood}>{post.mood}</span>
                            <span style={S.dot}>·</span>
                            <span>{post.date}</span>
                        </div>
                        <h2 style={S.cardTitle}>{post.title}</h2>
                        <p style={S.cardSummary}>{post.summary}</p>
                    </article>
                ))}
            </section>
        </div>
    );
}

const ACCENT = "#16a34a";

const S: Record<string, CSSProperties> = {
    page: {
        maxWidth: 720,
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
        border: "1px solid #eaf1ec",
        padding: "24px 26px",
        boxShadow: "0 8px 24px rgba(22, 101, 52, 0.05)",
    },
    cardMeta: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 13,
        color: "#9aa0b2",
    },
    mood: { fontWeight: 600, color: ACCENT },
    dot: { color: "#c7cbd9" },
    cardTitle: {
        margin: "12px 0 0",
        fontSize: 21,
        fontWeight: 700,
        color: "#1b2236",
        letterSpacing: "-0.01em",
    },
    cardSummary: {
        margin: "12px 0 0",
        fontSize: 15,
        lineHeight: 1.8,
        color: "#5a6072",
    },
};

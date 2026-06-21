import { useState } from "react";
import type { CSSProperties } from "react";

// ── 페르소나 정의 ─────────────────────────────
// route 는 실제 라우터 경로로 바꿔서 쓰세요 (예: navigate(p.route))
interface Persona {
    key: string;
    label: string;
    name: string;
    desc: string;
    emoji: string;
    accent: string;
    tint: string;
    iconBg: string;
    route: string;
}

interface PersonaGateProps {
    onSelect?: (key: string, route: string) => void;
}

const PERSONAS: Persona[] = [
    {
        key: "developer",
        label: "DEVELOPER",
        name: "개발 페르소나",
        desc: "코드, 프로젝트, 기술 기록",
        emoji: "👨‍💻",
        accent: "#6366f1",
        tint: "#eef0fe",
        iconBg: "#ece9fd",
        route: "/developer",
    },
    {
        key: "rider",
        label: "RIDER",
        name: "애슐리트 페르소나",
        desc: "운동 · 라이딩 기록과 계획",
        emoji: "🚴",
        accent: "#2563eb",
        tint: "#eaf0fe",
        iconBg: "#e6edfd",
        route: "/records/riding",
    },
    {
        key: "human",
        label: "HUMAN",
        name: "휴먼 페르소나",
        desc: "일상, 생각, 그리고 사람",
        emoji: "🌱",
        accent: "#16a34a",
        tint: "#eaf7ee",
        iconBg: "#e6f6ec",
        route: "/human",
    },
];

export default function PersonaGate({ onSelect }: PersonaGateProps) {
    // 화면 로드 시 RIDER 가 강조된 상태 (스크린샷과 동일)
    const [active, setActive] = useState("rider");

    const handleSelect = (p: Persona) => {
        if (onSelect) onSelect(p.key, p.route);
    };

    return (
        <>
            <style>{`
        .lm-gate {
          min-height: 100%;
          box-sizing: border-box;
          padding: 64px 24px;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          background: linear-gradient(160deg, #fafbff 0%, #eef0f8 100%);
          font-family: "Pretendard", -apple-system, "Apple SD Gothic Neo",
            system-ui, "Segoe UI", sans-serif;
          color: #1e2235;
        }
        .lm-title {
          margin: 0;
          font-size: 44px;
          font-weight: 800;
          letter-spacing: -0.02em;
          color: #1b2236;
        }
        .lm-subtitle {
          margin: 14px 0 0;
          font-size: 17px;
          font-weight: 400;
          color: #8a90a3;
        }
        .lm-cards {
          margin-top: 52px;
          width: 100%;
          max-width: 1020px;
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 28px;
          align-items: stretch;
        }
        .lm-card {
          background: #ffffff;
          border-radius: 20px;
          border: 2px solid transparent;
          padding: 38px 28px 32px;
          display: flex;
          flex-direction: column;
          align-items: center;
          text-align: center;
          box-shadow: 0 10px 30px rgba(30, 41, 90, 0.06);
          transition: transform 0.22s ease, box-shadow 0.22s ease,
            border-color 0.22s ease;
        }
        .lm-card.is-active {
          border-color: var(--accent);
          transform: translateY(-8px);
          box-shadow: 0 18px 42px rgba(37, 99, 235, 0.16);
        }
        .lm-icon {
          width: 84px;
          height: 84px;
          border-radius: 50%;
          background: var(--icon-bg);
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 38px;
          line-height: 1;
        }
        .lm-label {
          margin-top: 22px;
          font-size: 13px;
          font-weight: 700;
          letter-spacing: 0.14em;
          color: var(--accent);
        }
        .lm-name {
          margin-top: 10px;
          font-size: 23px;
          font-weight: 800;
          letter-spacing: -0.01em;
          color: #1b2236;
        }
        .lm-desc {
          margin-top: 12px;
          font-size: 15px;
          color: #8a90a3;
        }
        .lm-btn {
          margin-top: 26px;
          border: none;
          cursor: pointer;
          padding: 12px 26px;
          border-radius: 999px;
          font-size: 15px;
          font-weight: 700;
          font-family: inherit;
          background: var(--tint);
          color: var(--accent);
          transition: background 0.22s ease, color 0.22s ease,
            transform 0.12s ease;
        }
        .lm-card.is-active .lm-btn {
          background: var(--accent);
          color: #ffffff;
          box-shadow: 0 8px 18px rgba(37, 99, 235, 0.28);
        }
        .lm-btn:hover { transform: translateY(-1px); }
        .lm-btn:focus-visible,
        .lm-card:focus-within {
          outline: 3px solid rgba(37, 99, 235, 0.35);
          outline-offset: 3px;
        }
        @media (max-width: 760px) {
          .lm-cards { grid-template-columns: 1fr; max-width: 420px; }
          .lm-title { font-size: 36px; }
          .lm-card.is-active { transform: none; }
        }
        @media (prefers-reduced-motion: reduce) {
          .lm-card, .lm-btn { transition: none; }
          .lm-card.is-active { transform: none; }
        }
      `}</style>

            <div className="lm-gate">
                <h1 className="lm-title">LifeMetrics</h1>
                <p className="lm-subtitle">어떤 신지훈을 만나러 오셨나요?</p>

                <div className="lm-cards">
                    {PERSONAS.map((p) => (
                        <article
                            key={p.key}
                            className={`lm-card ${active === p.key ? "is-active" : ""}`}
                            style={{
                                "--accent": p.accent,
                                "--tint": p.tint,
                                "--icon-bg": p.iconBg,
                            } as CSSProperties}
                            onMouseEnter={() => setActive(p.key)}
                            onFocus={() => setActive(p.key)}
                        >
                            <div className="lm-icon" aria-hidden="true">
                                {p.emoji}
                            </div>
                            <div className="lm-label">{p.label}</div>
                            <div className="lm-name">{p.name}</div>
                            <div className="lm-desc">{p.desc}</div>
                            <button
                                type="button"
                                className="lm-btn"
                                onClick={() => handleSelect(p)}
                            >
                                들어가기 →
                            </button>
                        </article>
                    ))}
                </div>
            </div>
        </>
    );
}
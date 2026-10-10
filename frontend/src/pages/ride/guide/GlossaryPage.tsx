import {useEffect, useMemo, useState} from "react";
import {useLocation} from "react-router-dom";
import GlossaryText from "@/components/glossary/GlossaryText";
import {
    CYCLING_GLOSSARY,
    GLOSSARY_CATEGORIES,
    glossaryAnchor,
    type GlossaryCategory,
} from "@/data/cyclingGlossary";
import "@/styles/glossary.css";

// ── 자전거 용어 사전 ─────────────────────────────────────────────
// AI 분석 등에서 용어를 클릭하면 #term-... 으로 들어와 해당 카드로 스크롤·강조한다.
export default function GlossaryPage() {
    const location = useLocation();
    const [query, setQuery] = useState("");
    const [category, setCategory] = useState<GlossaryCategory | "전체">("전체");
    const [highlight, setHighlight] = useState<string | null>(null);

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        return CYCLING_GLOSSARY.filter((e) => {
            if (category !== "전체" && e.category !== category) return false;
            if (!q) return true;
            return [e.term, e.full ?? "", e.short, e.detail ?? "", ...(e.aliases ?? [])]
                .some((s) => s.toLowerCase().includes(q));
        });
    }, [query, category]);

    useEffect(() => {
        const id = location.hash.slice(1);   // glossaryAnchor 가 인코딩한 그대로 비교
        if (!id) return;
        // 필터에 가려져 있으면 풀어준다
        setQuery("");
        setCategory("전체");
        setHighlight(id);
        requestAnimationFrame(() => {
            document.getElementById(id)?.scrollIntoView({behavior: "smooth", block: "center"});
        });
        const t = setTimeout(() => setHighlight(null), 2500);
        return () => clearTimeout(t);
    }, [location.hash]);

    return (
        <div className="glossary-page">
            <h2>📖 자전거 용어 사전</h2>
            <p className="glossary-desc">
                AI 분석 등에서 점선 밑줄이 있는 단어에 마우스를 올리면 여기 있는 뜻이 떠요. 총 {CYCLING_GLOSSARY.length}개
            </p>

            <div className="glossary-toolbar">
                <input
                    className="glossary-search"
                    placeholder="용어 검색 (예: TSS, 케이던스)"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                />
                {(["전체", ...GLOSSARY_CATEGORIES] as const).map((c) => (
                    <button
                        key={c}
                        className={`glossary-chip${category === c ? " active" : ""}`}
                        onClick={() => setCategory(c)}
                    >
                        {c}
                    </button>
                ))}
            </div>

            {filtered.length === 0 && <div className="glossary-empty">찾는 용어가 없어요.</div>}

            {GLOSSARY_CATEGORIES.map((cat) => {
                const entries = filtered.filter((e) => e.category === cat);
                if (entries.length === 0) return null;
                return (
                    <section key={cat} className="glossary-section">
                        <h3 className="glossary-section-title">{cat}</h3>
                        <div className="glossary-grid">
                            {entries.map((e) => {
                                const id = glossaryAnchor(e.term);
                                return (
                                    <div
                                        key={e.term}
                                        id={id}
                                        className={`glossary-card${highlight === id ? " highlight" : ""}`}
                                    >
                                        <div className="glossary-card-head">
                                            <span className="glossary-card-term">{e.term}</span>
                                            {e.full && <span className="glossary-card-full">{e.full}</span>}
                                        </div>
                                        <p className="glossary-card-short"><GlossaryText>{e.short}</GlossaryText></p>
                                        {e.detail && (
                                            <p className="glossary-card-detail"><GlossaryText>{e.detail}</GlossaryText></p>
                                        )}
                                        {e.aliases && e.aliases.length > 0 && (
                                            <div className="glossary-card-aliases">
                                                {e.aliases.map((a) => <span key={a}>{a}</span>)}
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </section>
                );
            })}
        </div>
    );
}

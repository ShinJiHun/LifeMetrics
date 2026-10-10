import {useState, type ReactNode} from "react";
import {createPortal} from "react-dom";
import {Link} from "react-router-dom";
import {GLOSSARY_LOOKUP, GLOSSARY_PATTERN, glossaryAnchor, type GlossaryEntry} from "@/data/cyclingGlossary";
import "@/styles/glossary.css";

const TOOLTIP_WIDTH = 280;

// 스크롤 패널 안에서도 잘리지 않도록 body 에 고정 위치로 띄운다.
function GlossaryTerm({word, entry}: { word: string; entry: GlossaryEntry }) {
    const [pos, setPos] = useState<{ left: number; top: number; below: boolean } | null>(null);

    const show = (el: HTMLElement) => {
        const r = el.getBoundingClientRect();
        const left = Math.min(Math.max(8, r.left + r.width / 2 - TOOLTIP_WIDTH / 2), window.innerWidth - TOOLTIP_WIDTH - 8);
        // 위쪽 공간이 부족하면 단어 아래로
        const below = r.top < 140;
        setPos({left, top: below ? r.bottom + 8 : r.top - 8, below});
    };

    return (
        <>
            <Link
                to={`/guide/glossary#${glossaryAnchor(entry.term)}`}
                className="glossary-term"
                onMouseEnter={(e) => show(e.currentTarget)}
                onMouseLeave={() => setPos(null)}
                onFocus={(e) => show(e.currentTarget)}
                onBlur={() => setPos(null)}
            >
                {word}
            </Link>
            {pos && createPortal(
                <div
                    className={`glossary-tooltip${pos.below ? " below" : ""}`}
                    style={{left: pos.left, top: pos.top, width: TOOLTIP_WIDTH}}
                    role="tooltip"
                >
                    <div className="glossary-tooltip-head">
                        <strong>{entry.term}</strong>
                        {entry.full && <span>{entry.full}</span>}
                    </div>
                    <div className="glossary-tooltip-body">{entry.short}</div>
                    <div className="glossary-tooltip-foot">클릭하면 용어 사전으로 이동</div>
                </div>,
                document.body,
            )}
        </>
    );
}

/** 문자열 안의 사전 용어를 찾아 마우스를 올리면 뜻이 뜨는 단어로 바꾼다. */
export default function GlossaryText({children}: { children: string | null | undefined }) {
    if (!children) return null;

    const parts: ReactNode[] = [];
    let last = 0;
    for (const m of children.matchAll(GLOSSARY_PATTERN)) {
        const entry = GLOSSARY_LOOKUP.get(m[0]);
        if (!entry || m.index === undefined) continue;
        if (m.index > last) parts.push(children.slice(last, m.index));
        parts.push(<GlossaryTerm key={m.index} word={m[0]} entry={entry}/>);
        last = m.index + m[0].length;
    }
    if (last < children.length) parts.push(children.slice(last));

    return <>{parts}</>;
}

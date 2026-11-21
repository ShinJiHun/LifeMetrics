// 강연 목록 (관리자 전용) — 일기 메모에서 만든 강연 요약을 태그·연사·기간으로 걸러 본다. 카드에서 바로 고치고 게시할 수 있다.
import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
    errorMessage,
    fetchDiarySubMenus,
    fetchLectureFacets,
    fetchLectures,
    type DiarySubMenu,
    type Lecture,
    type LectureFacets,
    type LectureFilter,
} from "@/api/diary";
import LectureCard from "./LectureCard";
import "@/styles/markdown.css";
import "@/styles/diary.css";

const KEYS = ["tag", "speaker", "from", "to"] as const;

export default function LecturesPage() {
    const [params, setParams] = useSearchParams();
    const filter: LectureFilter = Object.fromEntries(KEYS.map((k) => [k, params.get(k) ?? ""]));
    const filterKey = KEYS.map((k) => params.get(k) ?? "").join("|");
    const setFilter = (patch: Partial<LectureFilter>) => {
        const next = { ...filter, ...patch };
        setParams(Object.fromEntries(Object.entries(next).filter(([, v]) => v)) as Record<string, string>, { replace: true });
    };

    const [lectures, setLectures] = useState<Lecture[] | null>(null);
    const [facets, setFacets] = useState<LectureFacets>({ tags: [], speakers: [] });
    const [subs, setSubs] = useState<DiarySubMenu[]>([]);
    const [lectureDefault, setLectureDefault] = useState<number | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState<string | null>(null);
    const [speakerInput, setSpeakerInput] = useState(filter.speaker ?? "");

    const load = useCallback(async () => {
        setError(null);
        try {
            const f = Object.fromEntries(filterKey.split("|").map((v, i) => [KEYS[i], v])) as LectureFilter;
            const [l, fc] = await Promise.all([fetchLectures(f), fetchLectureFacets()]);
            setLectures(l);
            setFacets(fc);
        } catch (e) {
            setError(errorMessage(e));
        }
    }, [filterKey]);

    useEffect(() => {
        load();
    }, [load]);

    const loadSubs = useCallback(() => {
        fetchDiarySubMenus()
            .then((r) => {
                setSubs(r.subs);
                setLectureDefault(r.lectureDefault);
            })
            .catch(() => setSubs([]));
    }, []);
    useEffect(loadSubs, [loadSubs]);

    const run = async (label: string, fn: () => Promise<unknown>) => {
        setBusy(label);
        setError(null);
        try {
            await fn();
            await load();
            if (label === "publish") loadSubs();
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(null);
        }
    };

    const hasFilter = KEYS.some((k) => filter[k]);

    return (
        <div className="diary-page">
            <header className="diary-head">
                <h1>🎤 강연</h1>
                <Link className="diary-link" to="/human/diary">
                    ← 일기
                </Link>
            </header>

            <div className="lecture-filter">
                <label>
                    태그
                    <select value={filter.tag} onChange={(e) => setFilter({ tag: e.target.value })}>
                        <option value="">전체</option>
                        {facets.tags.map((t) => (
                            <option key={t.name} value={t.name}>
                                {t.name} ({t.count})
                            </option>
                        ))}
                    </select>
                </label>
                <label>
                    연사
                    <input
                        list="lecture-speakers"
                        value={speakerInput}
                        onChange={(e) => setSpeakerInput(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && !e.nativeEvent.isComposing && setFilter({ speaker: speakerInput.trim() })}
                        onBlur={() => speakerInput.trim() !== (filter.speaker ?? "") && setFilter({ speaker: speakerInput.trim() })}
                        placeholder="이름 일부"
                    />
                    <datalist id="lecture-speakers">
                        {facets.speakers.map((s) => (
                            <option key={s.name} value={s.name}>
                                {s.count}건
                            </option>
                        ))}
                    </datalist>
                </label>
                <label>
                    기간
                    <input type="date" value={filter.from} onChange={(e) => setFilter({ from: e.target.value })} />
                    ~
                    <input type="date" value={filter.to} onChange={(e) => setFilter({ to: e.target.value })} />
                </label>
                {hasFilter && (
                    <button
                        className="diary-link"
                        onClick={() => {
                            setSpeakerInput("");
                            setParams({}, { replace: true });
                        }}
                    >
                        필터 지우기
                    </button>
                )}
            </div>

            {facets.tags.length > 0 && (
                <div className="lecture-tags lecture-tag-cloud">
                    {facets.tags.slice(0, 40).map((t) => (
                        <span key={t.name} className={`lecture-tag ${filter.tag === t.name ? "on" : ""}`}>
                            <button className="tag-name" onClick={() => setFilter({ tag: filter.tag === t.name ? "" : t.name })}>
                                #{t.name} <small>{t.count}</small>
                            </button>
                        </span>
                    ))}
                </div>
            )}

            {error && <div className="diary-error">{error}</div>}
            {!lectures && !error && <div className="diary-muted">불러오는 중…</div>}
            {lectures && (
                <>
                    <div className="diary-muted">{lectures.length}건{lectures.length >= 500 ? " (최근 500건까지)" : ""}</div>
                    {lectures.length === 0 && (
                        <p className="diary-muted">
                            {hasFilter ? "조건에 맞는 강연이 없습니다." : "아직 강연 요약이 없습니다. 일기 화면에 강연 필기 메모를 올리고 [🎤 강연 찾기]를 누르세요."}
                        </p>
                    )}
                    {lectures.map((l) => (
                        <div key={l.id}>
                            <Link className="diary-link" to={`/human/diary?date=${l.lectureDate}`}>
                                {l.lectureDate} 일기 화면 →
                            </Link>
                            <LectureCard
                                lecture={l}
                                subs={subs}
                                defaultSub={lectureDefault}
                                busy={!!busy}
                                run={run}
                                onTagClick={(t) => setFilter({ tag: t })}
                                collapsed
                            />
                        </div>
                    ))}
                </>
            )}
        </div>
    );
}

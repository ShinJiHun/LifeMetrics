// 휴먼 일기 (관리자 전용) — 하루 메모를 모으고, 밤 10시 배치가 만든 일기 초안을 확인·수정해 휴먼 블로그에 게시한다.
// 메모 출처: Claude 앱 대화 정리(파일·붙여넣기), Claude Code 대화 정리(서버는 NAS 폴더에 저장하면 자동으로 들어옴), 직접 입력.
// 문체 기준은 backend/src/main/resources/diary/style-guide.md 하나다.
import { useCallback, useEffect, useMemo, useState } from "react";
import type { ChangeEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
    addDiaryNote,
    deleteDiaryNote,
    discardDiaryEntry,
    errorMessage,
    fetchDiaryDay,
    fetchDiaryRecent,
    fetchDiarySubMenus,
    generateDiary,
    publishDiaryEntry,
    updateDiaryEntry,
    type DiaryDay,
    type DiaryEntry,
    type DiaryRecent,
    type DiarySubMenu,
    type NoteSource,
} from "@/api/diary";
import "@/styles/markdown.css";
import "@/styles/diary.css";

const SOURCE_LABEL: Record<NoteSource, string> = { app: "Claude 앱", cli: "Claude Code", manual: "직접" };

const kstToday = () => new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 10);
const shiftDate = (d: string, days: number) => {
    const t = new Date(`${d}T00:00:00Z`);
    t.setUTCDate(t.getUTCDate() + days);
    return t.toISOString().slice(0, 10);
};
const weekday = (d: string) => "일월화수목금토"[new Date(`${d}T00:00:00Z`).getUTCDay()];

/** Claude 앱 '일기' 프로젝트 지침에 붙여 넣을 문장. 대화를 메모 파일로 정리하게 한다. */
const APP_PROJECT_GUIDE = `이 프로젝트는 하루 일기 메모를 모으는 곳이다.
내가 "메모 정리해줘"라고 하면 이 대화에서 오늘 있었던 일·생각·읽은 책 내용만 뽑아 마크다운으로 정리해 줘.
- 파일 이름: app-YYYY-MM-DD-주제.md (날짜는 오늘)
- 내용: 일어난 일을 시간 순으로 개조식. 내 말투와 느낌을 살리고, 대화에 없는 내용은 지어내지 않는다.
- 책 내용이 있으면 "## 책" 아래에 제목, 읽은 범위, 인상 깊은 문장, 내 생각을 따로 정리.
- 일기 문장으로 다듬지 않는다(일기는 사이트가 문체 가이드대로 쓴다).`;

export default function DiaryPage() {
    const [params, setParams] = useSearchParams();
    const date = params.get("date") ?? kstToday();
    const setDate = (d: string) => setParams(d === kstToday() ? {} : { date: d }, { replace: true });

    const [day, setDay] = useState<DiaryDay | null>(null);
    const [recent, setRecent] = useState<DiaryRecent[]>([]);
    const [subs, setSubs] = useState<DiarySubMenu[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState<string | null>(null);
    const [showGuide, setShowGuide] = useState(false);

    const load = useCallback(async () => {
        setError(null);
        try {
            const [d, r] = await Promise.all([fetchDiaryDay(date), fetchDiaryRecent()]);
            setDay(d);
            setRecent(r);
        } catch (e) {
            setError(errorMessage(e));
        }
    }, [date]);

    useEffect(() => {
        setDay(null);
        load();
    }, [load]);

    useEffect(() => {
        fetchDiarySubMenus().then(setSubs).catch(() => setSubs([]));
    }, []);

    const run = async (label: string, fn: () => Promise<unknown>) => {
        setBusy(label);
        setError(null);
        try {
            await fn();
            await load();
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(null);
        }
    };

    const onGenerate = () => {
        const edited = day?.diary?.edited || day?.book?.edited;
        if (edited && !window.confirm("직접 고친 초안이 있습니다. 다시 만들면 고친 내용이 사라집니다. 계속할까요?")) return;
        run("generate", () => generateDiary(date));
    };

    const recordSummary = useMemo(() => summarizeRecords(day?.records), [day]);

    return (
        <div className="diary-page">
            <header className="diary-head">
                <h1>📔 일기</h1>
                <div className="diary-datenav">
                    <button onClick={() => setDate(shiftDate(date, -1))} aria-label="전날">◀</button>
                    <input type="date" value={date} max={kstToday()} onChange={(e) => e.target.value && setDate(e.target.value)} />
                    <span className="diary-weekday">({weekday(date)})</span>
                    <button onClick={() => setDate(shiftDate(date, 1))} disabled={date >= kstToday()} aria-label="다음날">▶</button>
                    {date !== kstToday() && <button onClick={() => setDate(kstToday())}>오늘</button>}
                </div>
                <button className="diary-link" onClick={() => setShowGuide((v) => !v)}>
                    {showGuide ? "사용법 닫기" : "사용법"}
                </button>
            </header>

            {showGuide && <Guide />}

            {recent.length > 0 && (
                <div className="diary-recent">
                    {recent.slice(0, 14).map((r) => (
                        <button key={r.date} className={`chip ${r.date === date ? "on" : ""}`} onClick={() => setDate(r.date)}>
                            {r.date.slice(5)}
                            <span className={`dot ${r.diary ?? "none"}`} title={r.diary === "published" ? "게시함" : r.diary === "draft" ? "초안" : "초안 없음"} />
                        </button>
                    ))}
                </div>
            )}

            {error && <div className="diary-error">{error}</div>}
            {!day && !error && <div className="diary-muted">불러오는 중…</div>}

            {day && (
                <div className="diary-grid">
                    <section className="diary-col">
                        <h2>
                            메모 <span className="diary-count">{day.notes.length}</span>
                        </h2>
                        {recordSummary && <div className="diary-records">오늘 기록: {recordSummary}</div>}
                        {day.notes.length === 0 && <p className="diary-muted">아직 메모가 없습니다. 아래에 붙여넣거나 파일을 올리세요.</p>}
                        {day.notes.map((n) => (
                            <NoteCard key={n.id} note={n} onDelete={() => run("note", () => deleteDiaryNote(n.id))} />
                        ))}
                        <NoteForm date={date} busy={busy === "note"} onAdd={(fn) => run("note", fn)} />
                    </section>

                    <section className="diary-col">
                        <div className="diary-entry-head">
                            <h2>초안</h2>
                            {day.diary?.status !== "published" && (
                                <button className="primary" onClick={onGenerate} disabled={!!busy}>
                                    {busy === "generate" ? "쓰는 중… (1분 정도)" : day.diary ? "다시 만들기" : "지금 만들기"}
                                </button>
                            )}
                        </div>
                        {!day.diary && (
                            <p className="diary-muted">
                                매일 밤 10시에 그날 메모와 운동 기록으로 초안을 만듭니다. 그 뒤에 올린 메모는 [다시 만들기]로 반영하세요.
                            </p>
                        )}
                        {day.diary && <EntryCard entry={day.diary} subs={subs} busy={!!busy} run={run} />}
                        {day.book && <EntryCard entry={day.book} subs={subs} busy={!!busy} run={run} />}
                    </section>
                </div>
            )}
        </div>
    );
}

function Guide() {
    const [copied, setCopied] = useState(false);
    return (
        <div className="diary-guide">
            <ol>
                <li>
                    <b>Claude 앱</b>: '일기' 프로젝트를 만들고 아래 지침을 넣어 두세요. 대화 끝에 "메모 정리해줘" → 받은 내용을 여기 붙여넣거나 파일로 올립니다.
                </li>
                <li>
                    <b>Claude Code (이 서버)</b>: "일기 메모 저장해줘" → <code>/mnt/200gb/NAS/diary/inbox/날짜/</code>에 저장되고, 이 화면을 열면 자동으로 들어옵니다.
                </li>
                <li>
                    <b>Claude Code (맥)</b>: 맥 폴더에 저장된 .md 파일을 여기서 올리거나 scp로 위 폴더에 보냅니다.
                </li>
                <li>
                    <b>밤 10시</b>: 초안이 만들어집니다. 자기 전에 확인·수정하고 [게시]하세요. 게시 전에는 블로그에 나가지 않습니다.
                </li>
            </ol>
            <div className="diary-guide-box">
                <pre>{APP_PROJECT_GUIDE}</pre>
                <button
                    onClick={() =>
                        navigator.clipboard.writeText(APP_PROJECT_GUIDE).then(() => {
                            setCopied(true);
                            setTimeout(() => setCopied(false), 1500);
                        })
                    }
                >
                    {copied ? "복사함" : "앱 프로젝트 지침 복사"}
                </button>
            </div>
        </div>
    );
}

function NoteCard({ note, onDelete }: { note: DiaryDay["notes"][number]; onDelete: () => void }) {
    const [open, setOpen] = useState(note.content.length < 400);
    return (
        <div className="diary-note">
            <div className="diary-note-head">
                <span className={`src src-${note.source}`}>{SOURCE_LABEL[note.source]}</span>
                {note.fileName && <span className="fname">{note.fileName}</span>}
                <span className="time">{note.createdAt.slice(11, 16)}</span>
                <button className="diary-link" onClick={() => setOpen((v) => !v)}>
                    {open ? "접기" : "펼치기"}
                </button>
                <button className="diary-link danger" onClick={() => window.confirm("이 메모를 지울까요?") && onDelete()}>
                    삭제
                </button>
            </div>
            <div className={`diary-note-body ${open ? "" : "clamped"}`}>{note.content}</div>
        </div>
    );
}

function NoteForm({ date, busy, onAdd }: { date: string; busy: boolean; onAdd: (fn: () => Promise<unknown>) => void }) {
    const [text, setText] = useState("");
    const [source, setSource] = useState<NoteSource>("manual");

    const submit = () => {
        if (!text.trim()) return;
        onAdd(async () => {
            await addDiaryNote(date, source, text);
            setText("");
        });
    };

    const onFiles = (e: ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(e.target.files ?? []);
        e.target.value = "";
        if (!files.length) return;
        onAdd(async () => {
            for (const f of files) {
                const src: NoteSource = f.name.startsWith("app") ? "app" : f.name.startsWith("cli") ? "cli" : source;
                await addDiaryNote(date, src, await f.text(), f.name);
            }
        });
    };

    return (
        <div className="diary-form">
            <textarea
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder="오늘 있었던 일, Claude 대화 정리를 붙여넣기…"
                rows={5}
            />
            <div className="diary-form-row">
                <select value={source} onChange={(e) => setSource(e.target.value as NoteSource)}>
                    <option value="manual">직접 쓴 메모</option>
                    <option value="app">Claude 앱 정리</option>
                    <option value="cli">Claude Code 정리</option>
                </select>
                <label className="diary-file">
                    파일 올리기
                    <input type="file" accept=".md,.txt,text/markdown,text/plain" multiple onChange={onFiles} hidden />
                </label>
                <button className="primary" onClick={submit} disabled={busy || !text.trim()}>
                    {busy ? "저장 중…" : "메모 추가"}
                </button>
            </div>
        </div>
    );
}

function EntryCard({
    entry,
    subs,
    busy,
    run,
}: {
    entry: DiaryEntry;
    subs: DiarySubMenu[];
    busy: boolean;
    run: (label: string, fn: () => Promise<unknown>) => void;
}) {
    const [editing, setEditing] = useState(false);
    const [title, setTitle] = useState(entry.title);
    const [content, setContent] = useState(entry.content);
    const [visibility, setVisibility] = useState<"public" | "private">("public");
    const bookSubs = subs.filter((s) => s.category === "독서");
    const [subId, setSubId] = useState<number | undefined>(undefined);
    const published = entry.status === "published";

    useEffect(() => {
        setTitle(entry.title);
        setContent(entry.content);
        setEditing(false);
    }, [entry]);

    const publish = () => {
        if (entry.kind === "book" && !subId) return;
        const where = entry.kind === "diary" ? "일상 > 일기" : bookSubs.find((s) => s.id === subId)?.name;
        if (!window.confirm(`'${entry.title}'을(를) ${where}에 ${visibility === "public" ? "공개" : "비공개"}로 게시할까요?`)) return;
        run("publish", () => publishDiaryEntry(entry.id, visibility, entry.kind === "book" ? subId : undefined));
    };

    return (
        <article className={`diary-entry ${published ? "published" : ""}`}>
            <div className="diary-entry-meta">
                <span className="kind">{entry.kind === "diary" ? "일기" : "독서"}</span>
                {published ? (
                    <span className="status ok">게시함 {entry.publishedAt?.slice(5, 16).replace("T", " ")}</span>
                ) : (
                    <span className="status">{entry.edited ? "고친 초안" : "초안"} · {entry.generatedAt.slice(5, 16).replace("T", " ")}</span>
                )}
                {published && entry.postId && (
                    <Link className="diary-link" to={`/human/post/${entry.postId}`}>
                        블로그에서 보기
                    </Link>
                )}
            </div>

            {!published && entry.warnings.length > 0 && (
                <div className="diary-warn">
                    <b>게시 전 확인</b>
                    <ul>
                        {entry.warnings.map((w, i) => (
                            <li key={i}>{w}</li>
                        ))}
                    </ul>
                </div>
            )}

            {editing ? (
                <div className="diary-edit">
                    <input value={title} onChange={(e) => setTitle(e.target.value)} />
                    <textarea value={content} onChange={(e) => setContent(e.target.value)} rows={18} />
                    <div className="diary-form-row">
                        <button onClick={() => setEditing(false)}>취소</button>
                        <button
                            className="primary"
                            disabled={busy}
                            onClick={() => run("save", () => updateDiaryEntry(entry.id, title, content))}
                        >
                            저장
                        </button>
                    </div>
                </div>
            ) : (
                <>
                    <h3 className="diary-title">{entry.title}</h3>
                    <div className="md-body diary-body">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{entry.content}</ReactMarkdown>
                    </div>
                </>
            )}

            {!published && !editing && (
                <div className="diary-actions">
                    <button onClick={() => setEditing(true)} disabled={busy}>
                        수정
                    </button>
                    <button
                        className="danger"
                        disabled={busy}
                        onClick={() => window.confirm("이 초안을 버릴까요?") && run("discard", () => discardDiaryEntry(entry.id))}
                    >
                        버리기
                    </button>
                    <span className="spacer" />
                    {entry.kind === "book" && (
                        <select value={subId ?? ""} onChange={(e) => setSubId(e.target.value ? Number(e.target.value) : undefined)}>
                            <option value="">독서 소메뉴 선택</option>
                            {bookSubs.map((s) => (
                                <option key={s.id} value={s.id}>
                                    {s.name}
                                </option>
                            ))}
                        </select>
                    )}
                    <select value={visibility} onChange={(e) => setVisibility(e.target.value as "public" | "private")}>
                        <option value="public">공개</option>
                        <option value="private">비공개</option>
                    </select>
                    <button className="primary" onClick={publish} disabled={busy || (entry.kind === "book" && !subId)}>
                        게시
                    </button>
                </div>
            )}
        </article>
    );
}

/** 그날 기록을 한 줄로: "웨이트 1회 · 라이딩 32km · 체성분 측정" */
function summarizeRecords(records?: Record<string, unknown>) {
    if (!records) return null;
    const parts: string[] = [];
    const arr = (k: string) => (Array.isArray(records[k]) ? (records[k] as Record<string, unknown>[]) : []);
    const fitness = arr("fitness");
    if (fitness.length) parts.push(`헬스 ${fitness.length}회`);
    const rides = arr("rides");
    if (rides.length) {
        const km = rides.reduce((s, r) => s + (Number(r.distanceM) || 0), 0) / 1000;
        parts.push(`라이딩 ${rides.length}회 ${km.toFixed(1)}km`);
    }
    if (arr("bodyRecords").length) parts.push("체성분 측정");
    if (arr("phoneExercises").length) parts.push(`폰 운동 ${arr("phoneExercises").length}건`);
    return parts.length ? parts.join(" · ") : null;
}

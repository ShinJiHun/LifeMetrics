// src/pages/ride/analysis/WorkLogPage.tsx
// 업무 일지: 오늘 한 일을 채팅으로 적으면 AI 가 금일 실적 / 명일 계획 / 이슈로 정리, 주간보고는 일일 기록을 모아 생성.
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import api from "@/lib/axios";
import "@/styles/worklog.css";

interface Entry { logDate: string; company: string | null; todayDone: string | null; tomorrowPlan: string | null; issues: string | null; }
interface Message { role: "user" | "assistant"; content: string; createdAt?: string; }
interface Weekly { weekStart: string; doneSummary: string | null; nextPlan: string | null; issues: string | null; days: Entry[]; }

const DAY_KO = ["일", "월", "화", "수", "목", "금", "토"];
const ymd = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const parse = (s: string) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };
const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const mondayOf = (d: Date) => addDays(d, -((d.getDay() + 6) % 7));
const label = (s: string) => { const d = parse(s); return `${d.getMonth() + 1}/${d.getDate()} (${DAY_KO[d.getDay()]})`; };
const errMsg = (e: unknown) =>
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message || "요청에 실패했습니다.";

const SECTIONS = [
    { key: "todayDone", title: "금일 실적" },
    { key: "tomorrowPlan", title: "명일 계획" },
    { key: "issues", title: "이슈" },
] as const;
const WEEKLY_SECTIONS = [
    { key: "doneSummary", title: "주간 실적" },
    { key: "nextPlan", title: "차주 계획" },
    { key: "issues", title: "이슈" },
] as const;

type Tab = "daily" | "weekly" | "upload" | "career";

/** initialTab: 메뉴 "📤 보고 JSON 업로드"(/persona/developer/worklog/upload)로 들어오면 업로드 탭부터 연다. */
export default function WorkLogPage({ initialTab = "daily" }: { initialTab?: Tab }) {
    const [tab, setTab] = useState<Tab>(initialTab);
    useEffect(() => setTab(initialTab), [initialTab]);
    const [date, setDate] = useState(ymd(new Date()));
    const [weekEntries, setWeekEntries] = useState<Entry[]>([]);

    const weekStart = mondayOf(parse(date));
    const weekDays = Array.from({ length: 7 }, (_, i) => ymd(addDays(weekStart, i)));

    const loadWeek = () => {
        api.get<Entry[]>("/worklog", { params: { from: weekDays[0], to: weekDays[6] } })
            .then(({ data }) => setWeekEntries(data))
            .catch(() => setWeekEntries([]));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    useEffect(loadWeek, [weekDays[0]]);

    return (
        <div className="worklog-page">
            <h2>🗂 업무 일지</h2>
            <p className="worklog-notice">
                🔒 고객 정보·IP·서버명·계정·내부 시스템 이름은 적지 말고, <b>내가 한 일을 내 말로</b> 적어주세요.
            </p>

            <div className="worklog-week">
                <button onClick={() => setDate(ymd(addDays(parse(date), -7)))} aria-label="이전 주">◀</button>
                <div className="worklog-days">
                    {weekDays.map((d) => {
                        const has = weekEntries.some((e) => e.logDate === d);
                        return (
                            <button key={d} className={`wd ${d === date ? "sel" : ""} ${has ? "has" : ""} ${d === ymd(new Date()) ? "today" : ""}`}
                                    onClick={() => { setDate(d); setTab("daily"); }}>
                                <span className="dow">{DAY_KO[parse(d).getDay()]}</span>
                                <span className="dn">{parse(d).getDate()}</span>
                                <span className="dot" />
                            </button>
                        );
                    })}
                </div>
                <button onClick={() => setDate(ymd(addDays(parse(date), 7)))} aria-label="다음 주">▶</button>
            </div>

            <div className="worklog-tabs">
                <button className={tab === "daily" ? "on" : ""} onClick={() => setTab("daily")}>일일보고 · {label(date)}</button>
                <button className={tab === "weekly" ? "on" : ""} onClick={() => setTab("weekly")}>
                    주간보고 · {weekStart.getMonth() + 1}/{weekStart.getDate()} 주
                </button>
                <button className={tab === "upload" ? "on" : ""} onClick={() => setTab("upload")}>📤 JSON 업로드</button>
                <button className={tab === "career" ? "on" : ""} onClick={() => setTab("career")}>🧾 경력기술서</button>
            </div>

            {tab === "daily" && <Daily key={date} date={date} onSaved={loadWeek} />}
            {tab === "weekly" && <WeeklyView key={weekDays[0]} weekStart={weekDays[0]} />}
            {tab === "upload" && <UploadView />}
            {tab === "career" && <CareerView />}
        </div>
    );
}

function Daily({ date, onSaved }: { date: string; onSaved: () => void }) {
    const [messages, setMessages] = useState<Message[]>([]);
    const [entry, setEntry] = useState<Record<string, string>>({ todayDone: "", tomorrowPlan: "", issues: "" });
    const [dirty, setDirty] = useState(false);
    const [input, setInput] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const endRef = useRef<HTMLDivElement>(null);

    const applyEntry = (e: Entry | null) => setEntry({
        todayDone: e?.todayDone || "", tomorrowPlan: e?.tomorrowPlan || "", issues: e?.issues || "",
    });

    useEffect(() => {
        api.get<{ entry: Entry | null; messages: Message[] }>(`/worklog/${date}`)
            .then(({ data }) => { setMessages(data.messages); applyEntry(data.entry); })
            .catch((e) => setError(errMsg(e)));
    }, [date]);

    useEffect(() => { endRef.current?.scrollIntoView({ block: "nearest" }); }, [messages, busy]);

    const send = async (ev: FormEvent) => {
        ev.preventDefault();
        const text = input.trim();
        if (!text || busy) return;
        if (dirty && !confirm("직접 고친 내용이 저장되지 않았어요. 그래도 보낼까요? (AI 가 저장된 정리본 기준으로 다시 정리합니다)")) return;
        setBusy(true); setError(null);
        setMessages((m) => [...m, { role: "user", content: text }]);
        setInput("");
        try {
            const { data } = await api.post<{ reply: string; draft: Entry }>(`/worklog/${date}/chat`, { message: text });
            setMessages((m) => [...m, { role: "assistant", content: data.reply }]);
            applyEntry(data.draft); setDirty(false); onSaved();
        } catch (e) {
            setError(errMsg(e));
            setMessages((m) => m.slice(0, -1));
            setInput(text);
        } finally { setBusy(false); }
    };

    const save = async () => {
        setBusy(true); setError(null);
        try {
            const { data } = await api.put<Entry>(`/worklog/${date}`, entry);
            applyEntry(data); setDirty(false); onSaved();
        } catch (e) { setError(errMsg(e)); } finally { setBusy(false); }
    };

    const copy = () => {
        const text = SECTIONS.map((s) => `[${s.title}]\n${entry[s.key] || "- 없음"}`).join("\n\n");
        navigator.clipboard?.writeText(text);
    };

    return (
        <div className="worklog-grid">
            <section className="worklog-chat">
                <div className="chat-thread">
                    {messages.length === 0 && (
                        <div className="chat-empty">
                            오늘 한 일을 편하게 적어주세요.<br />
                            예) 청구 배치 오류 원인 찾음. 쿼리 타임아웃이라 인덱스 추가 제안. 내일 테스트 예정
                        </div>
                    )}
                    {messages.map((m, i) => <div key={i} className={`bubble ${m.role}`}>{m.content}</div>)}
                    {busy && <div className="bubble assistant typing">정리 중…</div>}
                    <div ref={endRef} />
                </div>
                <form className="chat-input" onSubmit={send}>
                    <textarea value={input} onChange={(e) => setInput(e.target.value)} rows={2}
                              placeholder="오늘 한 일, 내일 할 일, 이슈를 적어주세요"
                              onKeyDown={(e) => { if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) send(e); }} />
                    <button type="submit" disabled={busy || !input.trim()}>보내기</button>
                </form>
            </section>

            <section className="worklog-report">
                {SECTIONS.map((s) => (
                    <label key={s.key} className="report-field">
                        <span>{s.title}</span>
                        <textarea value={entry[s.key]} rows={s.key === "issues" ? 3 : 5}
                                  onChange={(e) => { setEntry({ ...entry, [s.key]: e.target.value }); setDirty(true); }}
                                  placeholder="- " />
                    </label>
                ))}
                {error && <div className="worklog-error">{error}</div>}
                <div className="report-actions">
                    <button onClick={copy} className="ghost">📋 복사</button>
                    <button onClick={save} disabled={busy || !dirty}>{dirty ? "💾 저장" : "저장됨"}</button>
                </div>
            </section>
        </div>
    );
}

function WeeklyView({ weekStart }: { weekStart: string }) {
    const [weekly, setWeekly] = useState<Weekly | null>(null);
    const [form, setForm] = useState<Record<string, string>>({ doneSummary: "", nextPlan: "", issues: "" });
    const [dirty, setDirty] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const apply = (w: Weekly) => {
        setWeekly(w);
        setForm({ doneSummary: w.doneSummary || "", nextPlan: w.nextPlan || "", issues: w.issues || "" });
        setDirty(false);
    };

    useEffect(() => {
        api.get<Weekly>(`/worklog/weekly/${weekStart}`).then(({ data }) => apply(data)).catch((e) => setError(errMsg(e)));
    }, [weekStart]);

    const run = async (fn: () => Promise<{ data: Weekly }>) => {
        setBusy(true); setError(null);
        try { apply((await fn()).data); } catch (e) { setError(errMsg(e)); } finally { setBusy(false); }
    };
    const generate = () => {
        if (weekly?.doneSummary && !confirm("지금 주간보고를 일일 기록 기준으로 다시 만들까요? 직접 고친 내용은 덮어씁니다.")) return;
        run(() => api.post<Weekly>(`/worklog/weekly/${weekStart}/generate`));
    };
    const save = () => run(() => api.put<Weekly>(`/worklog/weekly/${weekStart}`, form));
    const copy = () => navigator.clipboard?.writeText(
        WEEKLY_SECTIONS.map((s) => `[${s.title}]\n${form[s.key] || "- 없음"}`).join("\n\n"));

    return (
        <div className="worklog-grid">
            <section className="worklog-days-list">
                <div className="sub-title">이번 주 일일 기록 ({weekly?.days.length || 0}일)</div>
                {weekly?.days.length ? weekly.days.map((d) => (
                    <div key={d.logDate} className="day-card">
                        <div className="day-date">{label(d.logDate)}</div>
                        {d.todayDone && <pre>{d.todayDone}</pre>}
                        {d.issues && <pre className="issue">{d.issues}</pre>}
                    </div>
                )) : <div className="chat-empty">이 주에는 일일 기록이 없습니다.</div>}
            </section>
            <section className="worklog-report">
                {WEEKLY_SECTIONS.map((s) => (
                    <label key={s.key} className="report-field">
                        <span>{s.title}</span>
                        <textarea value={form[s.key]} rows={s.key === "doneSummary" ? 8 : 4} placeholder="- "
                                  onChange={(e) => { setForm({ ...form, [s.key]: e.target.value }); setDirty(true); }} />
                    </label>
                ))}
                {error && <div className="worklog-error">{error}</div>}
                <div className="report-actions">
                    <button onClick={generate} disabled={busy || !weekly?.days.length} className="ghost">
                        {busy ? "만드는 중…" : "✨ 일일 기록으로 만들기"}
                    </button>
                    <button onClick={copy} className="ghost">📋 복사</button>
                    <button onClick={save} disabled={busy || !dirty}>{dirty ? "💾 저장" : "저장됨"}</button>
                </div>
            </section>
        </div>
    );
}

interface Upload { id: number; fileName: string; sizeBytes: number; uploadedAt: string; }
interface ParseResult { inserted: number; updated: number; unchanged: number; skipped: string[]; entriesSynced?: number; }
const parseMsg = (p: ParseResult) =>
    `보고서 새로 ${p.inserted} · 변경 ${p.updated} · 동일 ${p.unchanged}` + (p.skipped.length ? ` · 건너뜀 ${p.skipped.length}` : "") + (p.entriesSynced ? ` · 업무 일지 ${p.entriesSynced}건 반영` : "");

function UploadView() {
    const [uploads, setUploads] = useState<Upload[]>([]);
    const [preview, setPreview] = useState<{ id: number; content: string } | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [done, setDone] = useState<string | null>(null);
    const fileRef = useRef<HTMLInputElement>(null);
    const [reloadKey, setReloadKey] = useState(0);

    const load = () => { api.get<Upload[]>("/worklog/uploads").then(({ data }) => setUploads(data)).catch((e) => setError(errMsg(e))); };
    useEffect(load, []);

    const onFiles = async (files: FileList | null) => {
        if (!files?.length) return;
        setBusy(true); setError(null); setDone(null);
        const ok: string[] = []; const fail: string[] = [];
        for (const f of Array.from(files)) {
            try {
                const { data } = await api.post<{ upload: Upload; parsed: ParseResult }>(
                    "/worklog/uploads", { fileName: f.name, content: await f.text() });
                ok.push(`${f.name} — ${parseMsg(data.parsed)}`);
                data.parsed.skipped.forEach((x) => fail.push(`${f.name}: ${x}`));
            } catch (e) { fail.push(`${f.name}: ${errMsg(e)}`); }
        }
        if (ok.length) setDone(`올렸습니다:\n${ok.join("\n")}`);
        if (fail.length) setError(fail.join("\n"));
        if (fileRef.current) fileRef.current.value = "";
        setBusy(false); load(); setReloadKey((k) => k + 1);
    };

    const reparse = async (u: Upload) => {
        setError(null); setDone(null);
        try {
            const { data } = await api.post<ParseResult>(`/worklog/uploads/${u.id}/parse`);
            setDone(`${u.fileName} 다시 파싱 — ${parseMsg(data)}`);
            if (data.skipped.length) setError(data.skipped.join("\n"));
            setReloadKey((k) => k + 1);
        } catch (e) { setError(errMsg(e)); }
    };

    const show = async (id: number) => {
        if (preview?.id === id) { setPreview(null); return; }
        try {
            const { data } = await api.get<Upload & { content: string }>(`/worklog/uploads/${id}`);
            let pretty = data.content;
            try { pretty = JSON.stringify(JSON.parse(data.content), null, 2); } catch { /* 원문 그대로 */ }
            setPreview({ id, content: pretty });
        } catch (e) { setError(errMsg(e)); }
    };

    const remove = async (u: Upload) => {
        if (!confirm(`${u.fileName} 을(를) 삭제할까요?`)) return;
        try { await api.delete(`/worklog/uploads/${u.id}`); if (preview?.id === u.id) setPreview(null); load(); }
        catch (e) { setError(errMsg(e)); }
    };

    return (
        <section className="worklog-report worklog-upload">
            <p className="sub-title">일일·주간보고 JSON 파일을 올리면 원본을 보관하고 날짜별 보고서로 풀어 저장합니다. 같은 날짜는 새 내용으로 덮어쓰니 누적 파일을 통째로 다시 올려도 됩니다 (여러 개 선택 가능, 파일당 최대 5MB).</p>
            <label className={`upload-drop ${busy ? "busy" : ""}`}
                   onDragOver={(e) => e.preventDefault()}
                   onDrop={(e) => { e.preventDefault(); onFiles(e.dataTransfer.files); }}>
                <input ref={fileRef} type="file" accept=".json,application/json" multiple disabled={busy}
                       onChange={(e) => onFiles(e.target.files)} />
                <span>{busy ? "올리는 중…" : "📂 파일 선택 또는 여기로 끌어놓기"}</span>
            </label>
            {done && <div className="upload-ok" style={{ whiteSpace: "pre-wrap" }}>{done}</div>}
            {error && <div className="worklog-error" style={{ whiteSpace: "pre-wrap" }}>{error}</div>}

            <div className="sub-title" style={{ marginTop: "1rem" }}>올린 파일 ({uploads.length})</div>
            {uploads.length === 0 && <div className="chat-empty">아직 올린 파일이 없습니다.</div>}
            {uploads.map((u) => (
                <div key={u.id} className="upload-row">
                    <button className="upload-name" onClick={() => show(u.id)}>{u.fileName}</button>
                    <span className="upload-meta">{(u.sizeBytes / 1024).toFixed(1)}KB · {u.uploadedAt.replace("T", " ").slice(0, 16)}</span>
                    <button className="ghost" onClick={() => reparse(u)} title="이 파일로 보고서 다시 풀기">🔄</button>
                    <button className="ghost" onClick={() => remove(u)} aria-label="삭제">🗑</button>
                    {preview?.id === u.id && <pre className="upload-preview">{preview.content}</pre>}
                </div>
            ))}
            <p className="upload-meta">원본 파일을 지워도 풀어 저장한 보고서는 남습니다.</p>

            <ReportList key={reloadKey} />
        </section>
    );
}

interface ReportSummary { kind: "daily" | "weekly"; reportDate: string; periodEnd: string | null; category: string | null; title: string | null; taskCount: number; }
interface SummaryItem { task?: string; content?: string; status?: string; }
interface DetailItem { heading?: string; items?: string[]; }
interface ReportDetail extends ReportSummary { summary: SummaryItem[] | null; plan: SummaryItem[] | null; details: DetailItem[] | null; }
interface TopicTask { date: string; task: string | null; content: string | null; status: string | null; }
interface TopicGroup { topic: string; taskCount: number; dayCount: number; firstDate: string; lastDate: string; tasks: TopicTask[]; }

const periodLabel = (r: ReportSummary) =>
    r.kind === "weekly" ? `${label(r.reportDate)} ~ ${r.periodEnd ? label(r.periodEnd) : "?"}` : label(r.reportDate);

/** 업로드 JSON 을 풀어 저장한 보고서: 날짜별 목록 / 주제별 묶음(포트폴리오 초안 재료). */
function ReportList() {
    const [view, setView] = useState<"daily" | "weekly" | "topic">("daily");
    const [rows, setRows] = useState<ReportSummary[]>([]);
    const [topics, setTopics] = useState<TopicGroup[]>([]);
    const [openTopic, setOpenTopic] = useState<string | null>(null);
    const [open, setOpen] = useState<ReportDetail | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        setOpen(null); setError(null);
        if (view === "topic") {
            api.get<TopicGroup[]>("/worklog/reports/topics").then(({ data }) => setTopics(data)).catch((e) => setError(errMsg(e)));
        } else {
            api.get<ReportSummary[]>("/worklog/reports", { params: { kind: view } }).then(({ data }) => setRows(data)).catch((e) => setError(errMsg(e)));
        }
    }, [view]);

    const toggle = async (r: ReportSummary) => {
        if (open && open.kind === r.kind && open.reportDate === r.reportDate) { setOpen(null); return; }
        try { const { data } = await api.get<ReportDetail>(`/worklog/reports/${r.kind}/${r.reportDate}`); setOpen(data); }
        catch (e) { setError(errMsg(e)); }
    };

    return (
        <div className="report-list">
            <div className="sub-title" style={{ marginTop: "1.25rem" }}>저장된 보고서</div>
            <div className="report-switch">
                <button className={view === "daily" ? "on" : ""} onClick={() => setView("daily")}>일일</button>
                <button className={view === "weekly" ? "on" : ""} onClick={() => setView("weekly")}>주간</button>
                <button className={view === "topic" ? "on" : ""} onClick={() => setView("topic")}>주제별</button>
            </div>
            {error && <div className="worklog-error">{error}</div>}

            {view !== "topic" && rows.length === 0 && <div className="chat-empty">저장된 보고서가 없습니다.</div>}
            {view !== "topic" && rows.map((r) => {
                const isOpen = open?.kind === r.kind && open.reportDate === r.reportDate;
                return (
                    <div key={r.kind + r.reportDate} className="report-row">
                        <button className="report-head" onClick={() => toggle(r)}>
                            <b>{periodLabel(r)}</b>
                            <span className="report-cat">{r.category || "(분류 없음)"}</span>
                            <span className="upload-meta">{r.taskCount}건</span>
                        </button>
                        {isOpen && open && <ReportBody r={open} />}
                    </div>
                );
            })}

            {view === "topic" && topics.length === 0 && <div className="chat-empty">저장된 일일보고가 없습니다.</div>}
            {view === "topic" && topics.map((g) => (
                <div key={g.topic} className="report-row">
                    <button className="report-head" onClick={() => setOpenTopic(openTopic === g.topic ? null : g.topic)}>
                        <b>{g.topic}</b>
                        <span className="report-cat">{label(g.firstDate)} ~ {label(g.lastDate)}</span>
                        <span className="upload-meta">{g.dayCount}일 · 과제 {g.taskCount}건</span>
                    </button>
                    {openTopic === g.topic && (
                        <ul className="report-items report-body">
                            {g.tasks.map((t, i) => (
                                <li key={i}>
                                    <span className="upload-meta">{label(t.date)}</span> <b>{t.task}</b>
                                    {t.content && <> — {t.content}</>}
                                    {t.status && <span className="report-status">{t.status}</span>}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            ))}
        </div>
    );
}

function ReportBody({ r }: { r: ReportDetail }) {
    return (
        <div className="report-body">
            {r.summary && r.summary.length > 0 && <>
                <div className="report-sec">{r.kind === "weekly" ? "주간 실적" : "금일 실적"}</div>
                <ul className="report-items">
                    {r.summary.map((s, i) => (
                        <li key={i}><b>{s.task}</b> — {s.content}{s.status && <span className="report-status">{s.status}</span>}</li>
                    ))}
                </ul>
            </>}
            {r.plan && r.plan.length > 0 && <>
                <div className="report-sec">차주 계획</div>
                <ul className="report-items">{r.plan.map((s, i) => <li key={i}><b>{s.task}</b> — {s.content}</li>)}</ul>
            </>}
            {r.details?.map((d, i) => (
                <div key={i}>
                    <div className="report-sec">{d.heading}</div>
                    <ul className="report-items">{d.items?.map((x, j) => <li key={j}>{x}</li>)}</ul>
                </div>
            ))}
        </div>
    );
}

// ── 경력기술서 초안: 보고서 → AI 초안 → 확인·수정 → 반영(승인) ──
interface DraftSummary { id: number; status: "draft" | "applied"; triggerType: string; sourceFrom: string | null; sourceTo: string | null; reportCount: number; createdAt: string; appliedAt: string | null; }
interface DraftCompany { companyName: string; shortName: string; domain: string; role: string; startDate: string; stack: string; }
interface DraftProject { title: string; periodLabel: string; intro: string; summary: string; stack: string; tasks: string[]; }
interface DraftBody { company: DraftCompany; project: DraftProject; }
interface CurrentCareer { companyName?: string; periodLabel?: string; role?: string; title?: string; projectPeriod?: string; paragraphs?: string; tasks?: string[]; }
interface DraftDetail extends DraftSummary { draft: DraftBody; warnings: string[]; current: CurrentCareer | null; }

const stripHtml = (h: string) => h.replace(/<\/p>/g, "\n").replace(/<[^>]+>/g, "").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&").trim();
const when = (s: string) => s.replace("T", " ").slice(0, 16);

function CareerView() {
    const [drafts, setDrafts] = useState<DraftSummary[]>([]);
    const [cur, setCur] = useState<DraftDetail | null>(null);
    const [edit, setEdit] = useState<DraftBody | null>(null);
    const [dirty, setDirty] = useState(false);
    const [busy, setBusy] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [msg, setMsg] = useState<string | null>(null);

    const loadList = () => api.get<DraftSummary[]>("/worklog/career-drafts").then(({ data }) => setDrafts(data)).catch((e) => setError(errMsg(e)));
    const open = (d: DraftDetail) => { setCur(d); setEdit(structuredClone(d.draft)); setDirty(false); };
    useEffect(() => { loadList(); }, []);

    const run = async (label: string, fn: () => Promise<void>) => {
        setBusy(label); setError(null); setMsg(null);
        try { await fn(); } catch (e) { setError(errMsg(e)); } finally { setBusy(null); }
    };
    const generate = () => run("초안 만드는 중… (30초~1분)", async () => {
        const { data } = await api.post<DraftDetail>("/worklog/career-drafts", null, { timeout: 180000 });
        open(data); loadList();
    });
    const select = (id: number) => run("불러오는 중…", async () => { open((await api.get<DraftDetail>(`/worklog/career-drafts/${id}`)).data); });
    const save = () => run("저장 중…", async () => {
        if (!cur || !edit) return;
        open((await api.put<DraftDetail>(`/worklog/career-drafts/${cur.id}`, edit)).data); setMsg("저장했습니다.");
    });
    const apply = () => run("반영 중…", async () => {
        if (!cur || !edit) return;
        if (cur.warnings.length && !confirm("내부 정보로 의심되는 표현이 남아 있습니다. 그래도 반영할까요?")) return;
        if (!confirm("경력기술서(포트폴리오)에 반영할까요? 현재 내용은 초안에 백업됩니다.")) return;
        if (dirty) await api.put(`/worklog/career-drafts/${cur.id}`, edit);
        open((await api.post<DraftDetail>(`/worklog/career-drafts/${cur.id}/apply`)).data); loadList();
        setMsg("경력기술서에 반영했습니다. 포트폴리오 화면에서 바로 확인할 수 있습니다.");
    });
    const discard = () => run("버리는 중…", async () => {
        if (!cur || !confirm("이 초안을 버릴까요?")) return;
        await api.delete(`/worklog/career-drafts/${cur.id}`); setCur(null); setEdit(null); loadList();
    });

    const setC = (k: keyof DraftCompany, v: string) => { if (edit) { setEdit({ ...edit, company: { ...edit.company, [k]: v } }); setDirty(true); } };
    const setP = (k: keyof DraftProject, v: string | string[]) => { if (edit) { setEdit({ ...edit, project: { ...edit.project, [k]: v } }); setDirty(true); } };
    const editable = cur?.status === "draft";

    return (
        <section className="worklog-report worklog-career">
            <p className="sub-title">
                저장된 일일·주간보고로 이번 프로젝트의 경력기술서 초안을 만듭니다. 내부 정보는 일반화해서 쓰고,
                <b> 반영을 눌러야만</b> 포트폴리오에 들어갑니다. 매주 금요일 21시에 보고서가 바뀌었으면 초안을 자동으로 만들어 둡니다.
            </p>
            <div className="report-switch">
                <button onClick={generate} disabled={!!busy}>✨ 새 초안 만들기</button>
            </div>
            {busy && <div className="upload-meta">{busy}</div>}
            {msg && <div className="upload-ok">{msg}</div>}
            {error && <div className="worklog-error">{error}</div>}

            <div className="sub-title" style={{ marginTop: "0.75rem" }}>초안 ({drafts.length})</div>
            {drafts.length === 0 && <div className="chat-empty">아직 초안이 없습니다.</div>}
            {drafts.map((d) => (
                <div key={d.id} className="report-row">
                    <button className="report-head" onClick={() => select(d.id)}>
                        <b>#{d.id}</b>
                        <span className="report-cat">
                            {d.sourceFrom && d.sourceTo ? `${label(d.sourceFrom)} ~ ${label(d.sourceTo)} 보고서 ${d.reportCount}건` : ""}
                        </span>
                        <span className={`report-status ${d.status}`}>{d.status === "applied" ? `반영됨 ${when(d.appliedAt || "")}` : "초안"}</span>
                        <span className="upload-meta">{d.triggerType === "batch" ? "자동" : "수동"} · {when(d.createdAt)}</span>
                    </button>
                </div>
            ))}

            {cur && edit && (
                <div className="career-edit">
                    <div className="sub-title" style={{ marginTop: "1rem" }}>초안 #{cur.id} {editable ? "— 확인·수정 후 반영" : "(반영됨, 읽기 전용)"}</div>
                    {cur.warnings.length > 0 && (
                        <div className="worklog-error">⚠️ 내부 정보로 의심되는 표현 — 확인 후 고쳐 주세요<ul>{cur.warnings.map((w) => <li key={w}>{w}</li>)}</ul></div>
                    )}
                    <fieldset disabled={!editable}>
                        <legend>회사</legend>
                        <label>회사명<input value={edit.company.companyName} onChange={(e) => setC("companyName", e.target.value)} /></label>
                        <label>짧은 이름<input value={edit.company.shortName} onChange={(e) => setC("shortName", e.target.value)} /></label>
                        <label>분야<input value={edit.company.domain} onChange={(e) => setC("domain", e.target.value)} /></label>
                        <label>역할<input value={edit.company.role} onChange={(e) => setC("role", e.target.value)} /></label>
                        <label>시작일<input value={edit.company.startDate} placeholder="YYYY-MM-DD" onChange={(e) => setC("startDate", e.target.value)} /></label>
                        <label>기술 스택<input value={edit.company.stack} onChange={(e) => setC("stack", e.target.value)} /></label>
                    </fieldset>
                    <fieldset disabled={!editable}>
                        <legend>프로젝트</legend>
                        <label>프로젝트명<input value={edit.project.title} onChange={(e) => setP("title", e.target.value)} /></label>
                        <label>기간<input value={edit.project.periodLabel} onChange={(e) => setP("periodLabel", e.target.value)} /></label>
                        <label>소개<textarea rows={3} value={edit.project.intro} onChange={(e) => setP("intro", e.target.value)} /></label>
                        <label>요약<input value={edit.project.summary} onChange={(e) => setP("summary", e.target.value)} /></label>
                        <label>사용 기술<input value={edit.project.stack} onChange={(e) => setP("stack", e.target.value)} /></label>
                        <label>주요 작업 (한 줄에 하나)
                            <textarea rows={Math.max(6, edit.project.tasks.length + 1)} value={edit.project.tasks.join("\n")}
                                      onChange={(e) => setP("tasks", e.target.value.split("\n"))} />
                        </label>
                    </fieldset>
                    {editable && (
                        <div className="report-switch">
                            <button onClick={save} disabled={!!busy || !dirty}>{dirty ? "💾 저장" : "저장됨"}</button>
                            <button className="on" onClick={apply} disabled={!!busy}>✅ 경력기술서에 반영</button>
                            <button onClick={discard} disabled={!!busy}>🗑 버리기</button>
                        </div>
                    )}

                    <div className="sub-title" style={{ marginTop: "1rem" }}>현재 경력기술서</div>
                    {!cur.current && <div className="chat-empty">아직 이 회사가 경력기술서에 없습니다. 반영하면 맨 위(현재 재직)로 새로 추가됩니다.</div>}
                    {cur.current && (
                        <div className="report-body">
                            <div><b>{cur.current.companyName}</b> <span className="upload-meta">{cur.current.periodLabel} · {cur.current.role}</span></div>
                            {cur.current.title ? <>
                                <div className="report-sec">{cur.current.title} <span className="upload-meta">{cur.current.projectPeriod}</span></div>
                                <div style={{ whiteSpace: "pre-wrap" }}>{stripHtml(cur.current.paragraphs || "")}</div>
                                <ul className="report-items">{cur.current.tasks?.map((t, i) => <li key={i}>{stripHtml(t)}</li>)}</ul>
                            </> : <div className="chat-empty">반영된 프로젝트가 없습니다.</div>}
                        </div>
                    )}
                </div>
            )}
        </section>
    );
}

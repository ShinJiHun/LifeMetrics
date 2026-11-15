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

export default function WorkLogPage() {
    const [tab, setTab] = useState<"daily" | "weekly">("daily");
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
            </div>

            {tab === "daily"
                ? <Daily key={date} date={date} onSaved={loadWeek} />
                : <WeeklyView key={weekDays[0]} weekStart={weekDays[0]} />}
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

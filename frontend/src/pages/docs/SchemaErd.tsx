// 구조·ERD (/docs/erd, 관리자 전용) — 지금 연결된 DB(riding·journal·lotto)의 실제 테이블과 관계를 보여 준다.
// 데이터: GET /api/admin/schema (information_schema 를 그대로 읽음, dev 는 *_dev). 관계는 선언된 외래 키(실선) + xxx_id 이름으로 추정(점선).
// 관계도는 mermaid 로 그리고, 이 화면을 열 때만 불러온다.
import { useEffect, useMemo, useRef, useState } from "react";
import api from "@/lib/axios";
import "@/styles/schema-erd.css";

interface Column {
    name: string;
    type: string;
    nullable: boolean;
    key: string;
    comment: string;
}
interface Table {
    name: string;
    view: boolean;
    rows: number | null;
    comment: string;
    columns: Column[];
}
interface Relation {
    fromTable: string;
    fromColumn: string;
    toTable: string;
    toColumn: string;
    declared: boolean;
}
interface Database {
    key: string;
    name: string | null;
    tables: Table[];
    relations: Relation[];
    error: string | null;
}

const DB_LABEL: Record<string, string> = { riding: "운동·라이딩", journal: "블로그·일기", lotto: "복권" };

/** 테이블 이름 첫 토큰으로 묶는다(activity_point → activity). */
const groupOf = (name: string) => name.replace(/^v_/, "").split("_")[0];

const safe = (s: string) => s.replace(/[^A-Za-z0-9_]/g, "_");
const typeWord = (t: string) => safe(t.replace(/\(.*$/, "").split(" ")[0]) || "col";
const quote = (s: string) => s.replace(/["\n\r]/g, " ").slice(0, 40);

/** 가운데 테이블은 컬럼 전부, 이웃은 키 컬럼만 넣어 그림이 너무 커지지 않게 한다. */
function erText(tables: Table[], rels: Relation[], focus: Set<string>): string {
    const fkCols = new Set(rels.map((r) => `${r.fromTable}.${r.fromColumn}`));
    const lines = ["erDiagram"];
    for (const t of tables) {
        const cols = focus.has(t.name)
            ? t.columns
            : t.columns.filter((c) => c.key === "PRI" || fkCols.has(`${t.name}.${c.name}`));
        lines.push(`  ${safe(t.name)} {`);
        for (const c of cols) {
            const keys = [c.key === "PRI" ? "PK" : "", fkCols.has(`${t.name}.${c.name}`) ? "FK" : ""].filter(Boolean).join(",");
            const note = c.comment ? ` "${quote(c.comment)}"` : "";
            lines.push(`    ${typeWord(c.type)} ${safe(c.name)}${keys ? " " + keys : ""}${note}`);
        }
        lines.push("  }");
    }
    for (const r of rels) {
        const line = r.declared ? "--" : "..";
        lines.push(`  ${safe(r.toTable)} ||${line}o{ ${safe(r.fromTable)} : "${quote(r.fromColumn)}"`);
    }
    return lines.join("\n");
}

let mermaidPromise: Promise<typeof import("mermaid").default> | null = null;
function loadMermaid() {
    mermaidPromise ??= import("mermaid").then((m) => {
        m.default.initialize({ startOnLoad: false, theme: "neutral", securityLevel: "strict", er: { useMaxWidth: false } });
        return m.default;
    });
    return mermaidPromise;
}

let renderSeq = 0;
function Diagram({ text }: { text: string }) {
    const ref = useRef<HTMLDivElement>(null);
    const [err, setErr] = useState<string | null>(null);
    const [fit, setFit] = useState(true);
    useEffect(() => {
        let alive = true;
        setErr(null);
        loadMermaid()
            .then((m) => m.render(`erd-${++renderSeq}`, text))
            .then(({ svg }) => {
                if (alive && ref.current) ref.current.innerHTML = svg;
            })
            .catch((e) => alive && setErr(String(e?.message ?? e)));
        return () => {
            alive = false;
        };
    }, [text]);
    if (err) return <div className="erd-error">관계도를 그리지 못했습니다: {err}</div>;
    return (
        <div
            ref={ref}
            className={`erd-diagram${fit ? " fit" : ""}`}
            onClick={() => setFit((v) => !v)}
            title={fit ? "눌러서 원래 크기로 보기" : "눌러서 화면 폭에 맞추기"}
        />
    );
}

export default function SchemaErd() {
    const [dbs, setDbs] = useState<Database[] | null>(null);
    const [loadErr, setLoadErr] = useState<string | null>(null);
    const [dbKey, setDbKey] = useState<string>("riding");
    const [q, setQ] = useState("");
    const [selected, setSelected] = useState<string | null>(null);
    const [group, setGroup] = useState<string | null>(null);
    const [showInferred, setShowInferred] = useState(true);

    useEffect(() => {
        api.get<Database[]>("/admin/schema")
            .then((r) => {
                setDbs(r.data);
                if (r.data.length && !r.data.some((d) => d.key === "riding")) setDbKey(r.data[0].key);
            })
            .catch((e) => setLoadErr(e?.response?.status === 403 ? "관리자로 로그인해야 볼 수 있습니다." : "스키마를 불러오지 못했습니다."));
    }, []);

    const db = dbs?.find((d) => d.key === dbKey);
    const rels = useMemo(() => (db?.relations ?? []).filter((r) => showInferred || r.declared), [db, showInferred]);
    const byName = useMemo(() => new Map((db?.tables ?? []).map((t) => [t.name, t])), [db]);

    const groups = useMemo(() => {
        const m = new Map<string, Table[]>();
        const needle = q.trim().toLowerCase();
        for (const t of db?.tables ?? []) {
            if (needle && !t.name.toLowerCase().includes(needle) && !t.comment.toLowerCase().includes(needle)) continue;
            const g = groupOf(t.name);
            m.set(g, [...(m.get(g) ?? []), t]);
        }
        return [...m.entries()].sort((a, b) => a[0].localeCompare(b[0]));
    }, [db, q]);

    // 선택한 테이블(또는 묶음)과 바로 이어진 테이블만 그린다
    const view = useMemo(() => {
        if (!db) return null;
        let center: Set<string>;
        if (selected) center = new Set([selected]);
        else if (group) center = new Set(db.tables.filter((t) => groupOf(t.name) === group).map((t) => t.name));
        else return null;
        const near = rels.filter((r) => center.has(r.fromTable) || center.has(r.toTable));
        const names = new Set([...center, ...near.flatMap((r) => [r.fromTable, r.toTable])]);
        const tables = [...names].map((n) => byName.get(n)).filter((t): t is Table => !!t);
        return { center, near, tables, text: erText(tables, near, selected ? center : new Set()) };
    }, [db, selected, group, rels, byName]);

    const pickDb = (k: string) => {
        setDbKey(k);
        setSelected(null);
        setGroup(null);
    };
    const pickTable = (n: string) => {
        setSelected(n);
        setGroup(null);
    };
    const pickGroup = (g: string) => {
        setGroup(g);
        setSelected(null);
    };

    if (loadErr) return <div className="erd-msg">{loadErr}</div>;
    if (!dbs) return <div className="erd-msg">스키마를 불러오는 중…</div>;

    const table = selected ? byName.get(selected) : undefined;
    const outgoing = selected ? rels.filter((r) => r.fromTable === selected) : [];
    const incoming = selected ? rels.filter((r) => r.toTable === selected) : [];

    return (
        <div className="erd">
            <div className="erd-bar">
                <div className="erd-dbs">
                    {dbs.map((d) => (
                        <button key={d.key} className={d.key === dbKey ? "on" : ""} onClick={() => pickDb(d.key)}>
                            {DB_LABEL[d.key] ?? d.key}
                            <small>{d.name ?? "?"} · {d.tables.length}</small>
                        </button>
                    ))}
                </div>
                <input className="erd-search" placeholder="테이블·설명 검색" value={q} onChange={(e) => setQ(e.target.value)} />
                <label className="erd-toggle">
                    <input type="checkbox" checked={showInferred} onChange={(e) => setShowInferred(e.target.checked)} />
                    이름으로 추정한 관계(점선) 포함
                </label>
            </div>

            {db?.error && <div className="erd-error">{db.error}</div>}

            <div className="erd-main">
                <aside className="erd-list">
                    {groups.map(([g, ts]) => (
                        <div key={g} className="erd-group">
                            <button className={`erd-group-name${group === g ? " on" : ""}`} onClick={() => pickGroup(g)} title="이 묶음 관계도 보기">
                                {g} <span>{ts.length}</span>
                            </button>
                            {ts.map((t) => (
                                <button key={t.name} className={`erd-item${selected === t.name ? " on" : ""}`} onClick={() => pickTable(t.name)}>
                                    <span className="erd-item-name">
                                        {t.view && <em>뷰</em>}
                                        {t.name}
                                    </span>
                                    {t.rows != null && <span className="erd-rows">{t.rows.toLocaleString()}</span>}
                                </button>
                            ))}
                        </div>
                    ))}
                    {groups.length === 0 && <div className="erd-msg">맞는 테이블이 없습니다.</div>}
                </aside>

                <section className="erd-detail">
                    {!view && (
                        <div className="erd-hint">
                            <p>왼쪽에서 테이블을 고르면 컬럼과 연결된 테이블을, 묶음 이름을 고르면 그 묶음 전체 관계도를 보여 줍니다.</p>
                            <p>
                                테이블 {db?.tables.length ?? 0}개 · 선언된 외래 키 {db?.relations.filter((r) => r.declared).length ?? 0}개 ·
                                이름으로 추정한 관계 {db?.relations.filter((r) => !r.declared).length ?? 0}개
                            </p>
                            <p className="erd-note">행 수는 information_schema 추정치라 실제와 조금 다를 수 있습니다.</p>
                        </div>
                    )}

                    {table && (
                        <div className="erd-table">
                            <h3>
                                {table.view && <em>뷰</em>}
                                {table.name}
                                {table.rows != null && <small> · 약 {table.rows.toLocaleString()}행</small>}
                            </h3>
                            {table.comment && <p className="erd-comment">{table.comment}</p>}
                            <table>
                                <thead>
                                    <tr>
                                        <th>컬럼</th>
                                        <th>타입</th>
                                        <th>키</th>
                                        <th>NULL</th>
                                        <th>설명</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {table.columns.map((c) => {
                                        const ref = outgoing.find((r) => r.fromColumn === c.name);
                                        return (
                                            <tr key={c.name}>
                                                <td className="mono">{c.name}</td>
                                                <td className="mono dim">{c.type}</td>
                                                <td>
                                                    {c.key === "PRI" && <span className="badge pk">PK</span>}
                                                    {c.key === "UNI" && <span className="badge uni">UNI</span>}
                                                    {c.key === "MUL" && <span className="badge idx">IDX</span>}
                                                    {ref && (
                                                        <button className={`badge fk${ref.declared ? "" : " guess"}`} onClick={() => pickTable(ref.toTable)}>
                                                            → {ref.toTable}
                                                        </button>
                                                    )}
                                                </td>
                                                <td className="dim">{c.nullable ? "예" : ""}</td>
                                                <td className="dim">{c.comment}</td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                            {incoming.length > 0 && (
                                <div className="erd-incoming">
                                    이 테이블을 가리키는 곳:
                                    {incoming.map((r) => (
                                        <button key={`${r.fromTable}.${r.fromColumn}`} className={`badge fk${r.declared ? "" : " guess"}`} onClick={() => pickTable(r.fromTable)}>
                                            {r.fromTable}.{r.fromColumn}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}

                    {view && view.tables.length > 0 && (
                        <>
                            {group && (
                                <h3>
                                    {group} 묶음 <small>· 테이블 {view.center.size}개, 연결 {view.near.length}개</small>
                                </h3>
                            )}
                            {view.tables.length > 40 ? (
                                <div className="erd-msg">연결된 테이블이 {view.tables.length}개라 관계도를 생략했습니다. 테이블을 하나 골라 주세요.</div>
                            ) : (
                                <Diagram text={view.text} />
                            )}
                            <p className="erd-note">실선: DB 에 선언된 외래 키 · 점선: 컬럼 이름(xxx_id)으로 추정한 관계</p>
                        </>
                    )}
                </section>
            </div>
        </div>
    );
}

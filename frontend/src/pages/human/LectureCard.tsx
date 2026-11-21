// 강연 요약 카드 — 일기 화면(그날 강연)과 강연 목록 화면에서 같이 쓴다. 보기·수정·태그·다시 만들기·버리기·게시.
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
    discardLecture,
    lectureInfoOf,
    publishLecture,
    regenerateLecture,
    setLectureTags,
    summarizeLecture,
    updateLecture,
    updateLectureInfo,
    type DiarySubMenu,
    type Lecture,
} from "@/api/diary";
import LectureInfoForm from "./LectureInfoForm";
import LectureFiles from "./LectureFiles";

/** 카드·게시 글 머리에 보이는 강연 정보 목록(빈 칸은 뺀다). */
function infoRows(l: Lecture): [string, string][] {
    const rows: [string, string][] = [];
    if (l.speaker) rows.push(["연사", l.speaker]);
    rows.push(["일시", `${l.lectureDate}${l.lectureTime ? ` ${l.lectureTime}` : ""}`]);
    if (l.venue) rows.push(["장소", l.venue]);
    if (l.host) rows.push(["주최", l.host]);
    if (l.series) rows.push(["시리즈", l.series]);
    if (l.books?.length) rows.push(["관련 책", l.books.map((b) => (b.startsWith("『") ? b : `『${b}』`)).join(", ")]);
    return rows;
}

export default function LectureCard({
    lecture,
    subs,
    defaultSub,
    busy,
    run,
    onTagClick,
    collapsed = false,
}: {
    lecture: Lecture;
    subs: DiarySubMenu[];
    /** 서버가 정한 '강연' 소메뉴. 없으면 게시할 때 '일상 > 강연'을 새로 만든다. */
    defaultSub?: number | null;
    busy: boolean;
    run: (label: string, fn: () => Promise<unknown>) => void;
    onTagClick?: (tag: string) => void;
    collapsed?: boolean;
}) {
    const [editing, setEditing] = useState(false);
    const [infoEditing, setInfoEditing] = useState(false);
    const [showMemo, setShowMemo] = useState(false);
    const [open, setOpen] = useState(!collapsed);
    const manual = lecture.origin === "manual";
    const hasSummary = lecture.content.trim().length > 0;
    const [title, setTitle] = useState(lecture.title);
    const [speaker, setSpeaker] = useState(lecture.speaker ?? "");
    const [content, setContent] = useState(lecture.content);
    const [tagInput, setTagInput] = useState("");
    const [visibility, setVisibility] = useState<"public" | "private">("public");
    const [picked, setPicked] = useState<number | undefined>(undefined);
    const subId = picked ?? defaultSub ?? undefined;
    const published = lecture.status === "published";

    useEffect(() => {
        setTitle(lecture.title);
        setSpeaker(lecture.speaker ?? "");
        setContent(lecture.content);
        setEditing(false);
        setInfoEditing(false);
    }, [lecture]);

    const summarize = () => {
        if (hasSummary && !window.confirm(lecture.edited ? "직접 고친 요약입니다. 다시 만들면 고친 내용이 사라집니다. 계속할까요?" : "요약을 다시 만들까요?"))
            return;
        run("lecture", () => summarizeLecture(lecture.id));
    };

    const saveTags = (tags: string[]) => run("tags", () => setLectureTags(lecture.id, tags));
    const addTag = () => {
        const parts = tagInput
            .split(",")
            .map((t) => t.trim().replace(/^#+/, "").trim())
            .filter(Boolean);
        if (!parts.length) return;
        const lower = new Set(lecture.tags.map((t) => t.toLowerCase()));
        const next = [...lecture.tags, ...parts.filter((t) => !lower.has(t.toLowerCase()))];
        setTagInput("");
        if (next.length !== lecture.tags.length) saveTags(next);
    };

    const regenerate = () => {
        if (lecture.sourceNoteId == null) return;
        const msg = lecture.edited
            ? "직접 고친 강연 요약입니다. 다시 만들면 고친 내용과 태그가 사라집니다. 계속할까요?"
            : "원본 메모로 이 강연 요약을 다시 만들까요?";
        if (!window.confirm(msg)) return;
        run("lecture", () => regenerateLecture(lecture.id));
    };

    const publish = () => {
        const s = subs.find((x) => x.id === subId);
        const where = s ? `${s.category} > ${s.name}` : "'일상 > 강연'(새로 만듦)";
        if (!window.confirm(`'${lecture.title}'을(를) ${where}에 ${visibility === "public" ? "공개" : "비공개"}로 게시할까요?`)) return;
        run("publish", () => publishLecture(lecture.id, visibility, subId));
    };

    return (
        <article className={`diary-entry lecture ${published ? "published" : ""}`} id={`lecture-${lecture.id}`}>
            <div className="diary-entry-meta">
                <span className="kind">🎤 강연{manual ? " · 직접 기록" : ""}</span>
                <span>{lecture.lectureDate}</span>
                {published ? (
                    <span className="status ok">게시함 {lecture.publishedAt?.slice(5, 16).replace("T", " ")}</span>
                ) : (
                    <span className="status">{lecture.edited ? "고친 초안" : "초안"} · {lecture.generatedAt.slice(5, 16).replace("T", " ")}</span>
                )}
                {lecture.noteFileName && <span className="fname">메모: {lecture.noteFileName}</span>}
                {!manual && lecture.sourceNoteId == null && <span>(원본 메모 삭제됨)</span>}
                {published && lecture.postId && (
                    <Link className="diary-link" to={`/human/post/${lecture.postId}`}>
                        블로그에서 보기
                    </Link>
                )}
                {collapsed && (
                    <button className="diary-link" onClick={() => setOpen((v) => !v)}>
                        {open ? "접기" : "펼치기"}
                    </button>
                )}
            </div>

            {infoEditing ? (
                <LectureInfoForm
                    initial={lectureInfoOf(lecture)}
                    busy={busy}
                    submitLabel="정보 저장"
                    showMemo={manual}
                    onCancel={() => setInfoEditing(false)}
                    onSubmit={(info) => run("save", () => updateLectureInfo(lecture.id, info))}
                />
            ) : editing ? (
                <div className="diary-edit">
                    <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="강연 제목" />
                    <input value={speaker} onChange={(e) => setSpeaker(e.target.value)} placeholder="연사 (없으면 비움)" />
                    <textarea value={content} onChange={(e) => setContent(e.target.value)} rows={18} placeholder="요약 (마크다운)" />
                    <div className="diary-form-row">
                        <button onClick={() => setEditing(false)}>취소</button>
                        <button
                            className="primary"
                            disabled={busy}
                            onClick={() => run("save", () => updateLecture(lecture.id, title, speaker, content))}
                        >
                            저장
                        </button>
                    </div>
                </div>
            ) : (
                <>
                    <h3 className="diary-title">{lecture.title}</h3>
                    <ul className="lecture-info">
                        {infoRows(lecture).map(([k, v]) => (
                            <li key={k}>
                                <b>{k}</b> {v}
                            </li>
                        ))}
                        {!lecture.speaker && <li className="diary-muted">연사 없음</li>}
                    </ul>
                </>
            )}

            {manual && lecture.memo && !infoEditing && (
                <div className="lecture-memo">
                    <button className="diary-link" onClick={() => setShowMemo((v) => !v)}>
                        {showMemo ? "내 메모 접기" : `내 메모 보기 (${lecture.memo.length.toLocaleString()}자)`}
                    </button>
                    {showMemo && <div className="diary-note-body">{lecture.memo}</div>}
                </div>
            )}
            {manual && (open || !hasSummary) && (
                <LectureFiles lectureId={lecture.id} files={lecture.files} busy={busy} editable={!published} run={run} />
            )}

            <div className="lecture-tags">
                {lecture.tags.map((t) => (
                    <span key={t} className="lecture-tag">
                        {onTagClick ? (
                            <button className="tag-name" onClick={() => onTagClick(t)} title="이 태그로 거르기">
                                #{t}
                            </button>
                        ) : (
                            <span className="tag-name">#{t}</span>
                        )}
                        <button
                            className="tag-x"
                            disabled={busy}
                            aria-label={`${t} 태그 지우기`}
                            onClick={() => saveTags(lecture.tags.filter((x) => x !== t))}
                        >
                            ×
                        </button>
                    </span>
                ))}
                <input
                    className="lecture-tag-input"
                    value={tagInput}
                    onChange={(e) => setTagInput(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === "Enter" && !e.nativeEvent.isComposing) {
                            e.preventDefault();
                            addTag();
                        }
                    }}
                    placeholder="태그 추가 (Enter, 쉼표로 여러 개)"
                    disabled={busy}
                />
            </div>

            {!published && lecture.warnings.length > 0 && (
                <div className="diary-warn">
                    <b>게시 전 확인</b>
                    <ul>
                        {lecture.warnings.map((w, i) => (
                            <li key={i}>{w}</li>
                        ))}
                    </ul>
                </div>
            )}

            {!editing && open && hasSummary && (
                <div className="md-body diary-body">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{lecture.content}</ReactMarkdown>
                </div>
            )}
            {!editing && !hasSummary && (
                <p className="diary-muted">아직 요약이 없습니다. 내 메모를 적거나 받아쓰기·PDF 자료를 올린 뒤 [요약 만들기]를 누르세요.</p>
            )}

            {!published && !editing && !infoEditing && (
                <div className="diary-actions">
                    <button onClick={() => setEditing(true)} disabled={busy}>
                        수정
                    </button>
                    <button onClick={() => setInfoEditing(true)} disabled={busy}>
                        정보 고치기
                    </button>
                    {manual && (
                        <button className={hasSummary ? "" : "primary"} onClick={summarize} disabled={busy}>
                            {hasSummary ? "요약 다시 만들기" : "요약 만들기"}
                        </button>
                    )}
                    {!manual && lecture.sourceNoteId != null && (
                        <button onClick={regenerate} disabled={busy}>
                            다시 만들기
                        </button>
                    )}
                    <button
                        className="danger"
                        disabled={busy}
                        onClick={() =>
                            window.confirm("이 강연 요약을 버릴까요? (메모가 바뀌기 전까지 자동으로 다시 만들지 않습니다)") &&
                            run("discard", () => discardLecture(lecture.id))
                        }
                    >
                        버리기
                    </button>
                    <span className="spacer" />
                    <select value={subId ?? ""} onChange={(e) => setPicked(e.target.value ? Number(e.target.value) : undefined)}>
                        {defaultSub == null && <option value="">일상 &gt; 강연 (새로 만듦)</option>}
                        {subs.map((s) => (
                            <option key={s.id} value={s.id}>
                                {s.category} &gt; {s.name}
                            </option>
                        ))}
                    </select>
                    <select value={visibility} onChange={(e) => setVisibility(e.target.value as "public" | "private")}>
                        <option value="public">공개</option>
                        <option value="private">비공개</option>
                    </select>
                    <button className="primary" onClick={publish} disabled={busy || !hasSummary} title={hasSummary ? "" : "요약을 먼저 만들어 주세요"}>
                        게시
                    </button>
                </div>
            )}
        </article>
    );
}

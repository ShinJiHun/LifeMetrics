import { useEffect } from "react";
import type { CSSProperties, ReactNode } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Underline from "@tiptap/extension-underline";
import TextAlign from "@tiptap/extension-text-align";
import Link from "@tiptap/extension-link";
import Image from "@tiptap/extension-image";
import Placeholder from "@tiptap/extension-placeholder";
import "@/styles/richtext.css";

export default function RichTextEditor({
    value,
    onChange,
    accent,
    placeholder,
}: {
    value: string;
    onChange: (html: string) => void;
    accent: string;
    placeholder?: string;
}) {
    const editor = useEditor({
        extensions: [
            StarterKit.configure({ link: false }),
            Underline,
            TextAlign.configure({ types: ["heading", "paragraph"] }),
            Link.configure({ openOnClick: false, autolink: true }),
            Image,
            Placeholder.configure({ placeholder: placeholder ?? "내용을 입력하세요" }),
        ],
        content: value,
        onUpdate: ({ editor }) => onChange(editor.getHTML()),
    });

    // 편집 중이 아닌 외부 변경(예: 글 불러오기)만 반영. 타이핑 중 커서 튐 방지.
    useEffect(() => {
        if (!editor) return;
        if (value !== editor.getHTML()) {
            editor.commands.setContent(value, { emitUpdate: false });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [editor]);

    if (!editor) return null;

    return (
        <div style={S.wrap}>
            <Toolbar editor={editor} accent={accent} />
            <EditorContent editor={editor} className="md-body rte-content" />
        </div>
    );
}

function Toolbar({ editor, accent }: { editor: NonNullable<ReturnType<typeof useEditor>>; accent: string }) {
    const setLink = () => {
        const prev = editor.getAttributes("link").href as string | undefined;
        const url = window.prompt("링크 주소", prev ?? "https://");
        if (url === null) return;
        if (url === "") {
            editor.chain().focus().extendMarkRange("link").unsetLink().run();
            return;
        }
        editor.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
    };

    const addImage = () => {
        const url = window.prompt("이미지 주소");
        if (!url) return;
        editor.chain().focus().setImage({ src: url }).run();
    };

    return (
        <div style={S.toolbar}>
            <Btn active={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()} accent={accent} title="굵게">B</Btn>
            <Btn active={editor.isActive("italic")} onClick={() => editor.chain().focus().toggleItalic().run()} accent={accent} title="기울임"><i>I</i></Btn>
            <Btn active={editor.isActive("underline")} onClick={() => editor.chain().focus().toggleUnderline().run()} accent={accent} title="밑줄"><u>U</u></Btn>
            <Btn active={editor.isActive("strike")} onClick={() => editor.chain().focus().toggleStrike().run()} accent={accent} title="취소선"><s>S</s></Btn>
            <Sep />
            <Btn active={editor.isActive("heading", { level: 1 })} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()} accent={accent} title="제목1">H1</Btn>
            <Btn active={editor.isActive("heading", { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} accent={accent} title="제목2">H2</Btn>
            <Btn active={editor.isActive("heading", { level: 3 })} onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()} accent={accent} title="제목3">H3</Btn>
            <Sep />
            <Btn active={editor.isActive({ textAlign: "left" })} onClick={() => editor.chain().focus().setTextAlign("left").run()} accent={accent} title="왼쪽 정렬">⯇</Btn>
            <Btn active={editor.isActive({ textAlign: "center" })} onClick={() => editor.chain().focus().setTextAlign("center").run()} accent={accent} title="가운데 정렬">≡</Btn>
            <Btn active={editor.isActive({ textAlign: "right" })} onClick={() => editor.chain().focus().setTextAlign("right").run()} accent={accent} title="오른쪽 정렬">⯈</Btn>
            <Btn active={editor.isActive({ textAlign: "justify" })} onClick={() => editor.chain().focus().setTextAlign("justify").run()} accent={accent} title="양쪽 정렬">☰</Btn>
            <Sep />
            <Btn active={editor.isActive("orderedList")} onClick={() => editor.chain().focus().toggleOrderedList().run()} accent={accent} title="번호 매기기">1.</Btn>
            <Btn active={editor.isActive("bulletList")} onClick={() => editor.chain().focus().toggleBulletList().run()} accent={accent} title="글머리 기호">•</Btn>
            <Btn active={editor.isActive("blockquote")} onClick={() => editor.chain().focus().toggleBlockquote().run()} accent={accent} title="인용">"</Btn>
            <Btn active={editor.isActive("codeBlock")} onClick={() => editor.chain().focus().toggleCodeBlock().run()} accent={accent} title="코드블록">{"</>"}</Btn>
            <Sep />
            <Btn active={editor.isActive("link")} onClick={setLink} accent={accent} title="링크">🔗</Btn>
            <Btn active={false} onClick={addImage} accent={accent} title="이미지">🖼</Btn>
            <Sep />
            <Btn active={false} onClick={() => editor.chain().focus().undo().run()} accent={accent} title="실행 취소">↺</Btn>
            <Btn active={false} onClick={() => editor.chain().focus().redo().run()} accent={accent} title="다시 실행">↻</Btn>
        </div>
    );
}

function Btn({
    active,
    onClick,
    accent,
    title,
    children,
}: {
    active: boolean;
    onClick: () => void;
    accent: string;
    title: string;
    children: ReactNode;
}) {
    return (
        <button
            type="button"
            title={title}
            onMouseDown={(e) => e.preventDefault()}
            onClick={onClick}
            style={{
                ...S.toolBtn,
                background: active ? accent : "transparent",
                color: active ? "#fff" : "#4b5162",
            }}
        >
            {children}
        </button>
    );
}

function Sep() {
    return <span style={S.sep} />;
}

const S: Record<string, CSSProperties> = {
    wrap: {
        borderRadius: 10, border: "1px solid #d8dbe6", background: "#fff",
        marginBottom: 22, overflow: "hidden",
    },
    toolbar: {
        display: "flex", flexWrap: "wrap", alignItems: "center", gap: 2,
        padding: "8px 10px", borderBottom: "1px solid #eceef3", background: "#f8f9fc",
    },
    toolBtn: {
        minWidth: 30, height: 30, padding: "0 8px", borderRadius: 7, border: "none",
        cursor: "pointer", fontSize: 13, fontWeight: 700, lineHeight: "30px",
    },
    sep: { width: 1, height: 20, background: "#e2e5ee", margin: "0 4px" },
};
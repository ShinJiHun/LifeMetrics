import DOMPurify from "dompurify";
import Markdown from "@/components/common/Markdown";
import "@/styles/post-body.css";

// 리치 에디터 도입 전 글은 마크다운 텍스트로 저장돼 있다.
// 새 글은 Tiptap이 만든 HTML(예: "<p>...")로 저장되므로, 시작 문자로 형식을 구분한다.
function isHtml(content: string): boolean {
    return /^\s*</.test(content);
}

// variant="post": 블로그 글 본문 스타일(styles/post-body.css) — 글 보기·미리보기·에디터가 같은 모양.
export default function RichText({ children, variant }: { children: string; variant?: "post" }) {
    if (isHtml(children)) {
        const safe = DOMPurify.sanitize(children, { ADD_ATTR: ["target", "rel"] });
        const cls = variant === "post" ? "md-body post-body" : "md-body";
        return <div className={cls} dangerouslySetInnerHTML={{ __html: safe }} />;
    }
    return <Markdown>{children}</Markdown>;
}
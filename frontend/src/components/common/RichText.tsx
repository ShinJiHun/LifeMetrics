import DOMPurify from "dompurify";
import Markdown from "@/components/common/Markdown";

// 리치 에디터 도입 전 글은 마크다운 텍스트로 저장돼 있다.
// 새 글은 Tiptap이 만든 HTML(예: "<p>...")로 저장되므로, 시작 문자로 형식을 구분한다.
function isHtml(content: string): boolean {
    return /^\s*</.test(content);
}

export default function RichText({ children }: { children: string }) {
    if (isHtml(children)) {
        const safe = DOMPurify.sanitize(children, { ADD_ATTR: ["target", "rel"] });
        return <div className="md-body" dangerouslySetInnerHTML={{ __html: safe }} />;
    }
    return <Markdown>{children}</Markdown>;
}
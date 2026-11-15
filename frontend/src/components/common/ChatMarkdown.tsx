// 챗 답변용 마크다운: 말풍선의 글자색·크기를 그대로 따르고 여백만 작게 준다(블로그용 Markdown 과 분리).
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "@/styles/chat-markdown.css";

export default function ChatMarkdown({ children }: { children: string }) {
    return (
        <div className="chat-md">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
        </div>
    );
}

/** 음성 읽기용: 마크다운 기호(**, #, -, `)를 뺀 평문 */
export const plainText = (md: string) =>
    md
        .replace(/```[\s\S]*?```/g, "")
        .replace(/`([^`]*)`/g, "$1")
        .replace(/\*\*([^*]+)\*\*/g, "$1")
        .replace(/\*([^*]+)\*/g, "$1")
        .replace(/^#{1,6}\s+/gm, "")
        .replace(/^\s*[-*]\s+/gm, "")
        .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1");

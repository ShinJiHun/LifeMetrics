package com.lifemetrics.backend.persona.diary;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일기 초안(마크다운)을 휴먼 블로그 본문(HTML)으로 바꾼다. 블로그 에디터(tiptap)가 읽는 태그만 쓴다.
 * 지원: ## / ### 제목, 문단, - 목록, 1. 목록, > 인용(줄마다), **굵게**, *기울임*, [링크](url). 그 밖의 문법은 글자 그대로 둔다.
 */
final class DiaryMarkdown {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");

    private DiaryMarkdown() {}

    static String toHtml(String md) {
        StringBuilder out = new StringBuilder();
        StringBuilder para = new StringBuilder();
        String list = null; // "ul" | "ol"
        for (String raw : md.replace("\r\n", "\n").split("\n")) {
            String line = raw.strip();
            String item = null, kind = null;
            if (line.startsWith("- ") || line.startsWith("* ")) { item = line.substring(2); kind = "ul"; }
            else if (line.matches("\\d+\\.\\s+.*")) { item = line.replaceFirst("\\d+\\.\\s+", ""); kind = "ol"; }

            if (line.isEmpty() || line.startsWith("#") || item != null) flush(out, para);
            if (list != null && !list.equals(kind)) { out.append("</").append(list).append('>'); list = null; }

            if (line.isEmpty()) continue;
            if (line.startsWith(">")) {
                flush(out, para);
                out.append("<blockquote><p>").append(inline(line.replaceFirst("^>\\s?", ""))).append("</p></blockquote>");
            } else if (line.startsWith("### ")) out.append("<h3>").append(inline(line.substring(4))).append("</h3>");
            else if (line.startsWith("## ") || line.startsWith("# ")) out.append("<h2>").append(inline(line.replaceFirst("^#+\\s+", ""))).append("</h2>");
            else if (item != null) {
                if (list == null) { out.append('<').append(kind).append('>'); list = kind; }
                out.append("<li><p>").append(inline(item)).append("</p></li>");
            } else {
                if (!para.isEmpty()) para.append("<br>");
                para.append(inline(line));
            }
        }
        flush(out, para);
        if (list != null) out.append("</").append(list).append('>');
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder para) {
        if (para.isEmpty()) return;
        out.append("<p>").append(para).append("</p>");
        para.setLength(0);
    }

    private static String inline(String s) {
        String h = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        h = LINK.matcher(h).replaceAll(m -> "<a href=\"" + Matcher.quoteReplacement(m.group(2)) + "\">"
                + Matcher.quoteReplacement(m.group(1)) + "</a>");
        h = BOLD.matcher(h).replaceAll("<strong>$1</strong>");
        h = ITALIC.matcher(h).replaceAll("<em>$1</em>");
        return h;
    }
}

package com.lifemetrics.backend.persona.diary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiaryMarkdownTest {

    @Test
    void headingsParagraphsAndLists() {
        String html = DiaryMarkdown.toHtml("""
                오늘은 좀 길었다..
                그래도 버텼다.

                ## 운동
                - 하체 **5세트**
                - 러닝머신 20분

                1. 첫째
                2. 둘째
                [당연한 말이지만 실천이 어렵다.]
                """);
        assertThat(html).isEqualTo("<p>오늘은 좀 길었다..<br>그래도 버텼다.</p>"
                + "<h2>운동</h2>"
                + "<ul><li><p>하체 <strong>5세트</strong></p></li><li><p>러닝머신 20분</p></li></ul>"
                + "<ol><li><p>첫째</p></li><li><p>둘째</p></li></ol>"
                + "<p>[당연한 말이지만 실천이 어렵다.]</p>");
    }

    @Test
    void escapesHtmlAndKeepsLinks() {
        assertThat(DiaryMarkdown.toHtml("<script>x</script> [책](https://example.com/a) *좋았다*"))
                .isEqualTo("<p>&lt;script&gt;x&lt;/script&gt; <a href=\"https://example.com/a\">책</a> <em>좋았다</em></p>");
    }

    @Test
    void blockquote() {
        assertThat(DiaryMarkdown.toHtml("## 인상 깊은 문장\n> AI는 다른 지능이다\n> 둘째 <줄>"))
                .isEqualTo("<h2>인상 깊은 문장</h2><blockquote><p>AI는 다른 지능이다</p></blockquote>"
                        + "<blockquote><p>둘째 &lt;줄&gt;</p></blockquote>");
    }

    @Test
    void starRatingIsNotItalic() {
        assertThat(DiaryMarkdown.toHtml("별점: ★★★★☆ (4.0)")).isEqualTo("<p>별점: ★★★★☆ (4.0)</p>");
    }
}

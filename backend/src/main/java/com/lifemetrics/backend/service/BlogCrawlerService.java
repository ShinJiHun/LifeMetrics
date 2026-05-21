package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.BlogPost;
import com.lifemetrics.backend.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 티스토리 블로그 수집기.
 * sitemap.xml 로 글 URL 을 전부 열거한 뒤(모바일 /m/ 제외) 각 글을 jsoup 으로 파싱한다.
 * RSS 는 본문 truncate·항목수 제한 위험이 있어 쓰지 않는다.
 * 제목/작성일/작성자는 스킨 무관한 OG 메타에서, 본문만 스킨 의존 셀렉터(설정값)에서 추출한다.
 */
@Service
@RequiredArgsConstructor
public class BlogCrawlerService {

    private final BlogPostRepository blogPostRepository;

    @Value("${persona.blog.base-url}")
    private String baseUrl;

    @Value("${persona.blog.sitemap-path:/sitemap.xml}")
    private String sitemapPath;

    /** 본문 셀렉터 후보(우선순위 순, 콤마 구분). 첫 매치 사용. 스킨마다 달라 설정값으로 분리. */
    @Value("${persona.blog.content-selectors:#article-view .tt_article_useless_p_margin,.tt_article_useless_p_margin,.area_view_content,.article_view}")
    private String contentSelectorsRaw;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 LifeMetricsBot/1.0";
    private static final int TIMEOUT_MS = 20000;

    /**
     * 전체 글을 크롤링해 upsert. 수집된(또는 갱신된) 글 수를 반환.
     */
    @Transactional
    public int crawlAll() {
        List<String> postUrls = collectPostUrls();
        Pattern postNoPattern = Pattern.compile(".*/(\\d+)$");

        int count = 0;
        for (String url : postUrls) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .get();

                String title = metaContent(doc, "meta[property=og:title]");
                if (title == null || title.isBlank()) {
                    title = doc.title();
                }

                String body = extractBody(doc);
                if (body == null || body.isBlank()) {
                    System.out.println("⚠️ [BlogCrawler] 본문 추출 실패, 건너뜀: " + url);
                    continue;
                }

                BlogPost post = blogPostRepository.findByUrl(url).orElseGet(BlogPost::new);
                post.setUrl(url);
                var m = postNoPattern.matcher(url);
                if (m.matches()) {
                    post.setPostNo(Integer.parseInt(m.group(1)));
                }
                post.setTitle(title != null ? title.trim() : null);
                post.setContent(body);
                post.setAuthor(metaContent(doc, "meta[name=by]"));
                post.setCategories(collectTags(doc));
                post.setPublishedAt(parsePublished(doc));
                blogPostRepository.save(post);
                count++;
            } catch (Exception e) {
                System.out.println("❌ [BlogCrawler] 글 파싱 실패: " + url + " -> " + e.getMessage());
            }
        }
        System.out.println("✅ [BlogCrawler] 수집 완료: " + count + "/" + postUrls.size() + "개");
        return count;
    }

    /** sitemap.xml 에서 글 URL 만 추려낸다(모바일 /m/ 제외, 숫자 글번호만). */
    private List<String> collectPostUrls() {
        String root = baseUrl.replaceAll("/+$", "");
        String sitemapUrl = root + sitemapPath;
        // 글 URL 패턴: https://xxx.tistory.com/숫자  (모바일 /m/숫자 제외)
        Pattern postUrl = Pattern.compile("^" + Pattern.quote(root) + "/\\d+$");

        try {
            String xml = Jsoup.connect(sitemapUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            Document sitemap = Jsoup.parse(xml, sitemapUrl, Parser.xmlParser());
            Set<String> urls = new LinkedHashSet<>();
            for (Element loc : sitemap.select("loc")) {
                String u = loc.text().trim();
                if (postUrl.matcher(u).matches()) {
                    urls.add(u);
                }
            }
            List<String> result = new ArrayList<>(urls);
            System.out.println("ℹ️ [BlogCrawler] sitemap 글 URL " + result.size() + "개 발견");
            return result;
        } catch (Exception e) {
            System.out.println("❌ [BlogCrawler] sitemap 파싱 실패: " + sitemapUrl + " -> " + e.getMessage());
            return List.of();
        }
    }

    private String extractBody(Document doc) {
        for (String selector : contentSelectorsRaw.split(",")) {
            String sel = selector.trim();
            if (sel.isEmpty()) continue;
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String text = el.text().trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String metaContent(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return el != null ? el.attr("content") : null;
    }

    private String collectTags(Document doc) {
        String tags = doc.select("meta[property=article:tag]").stream()
                .map(e -> e.attr("content").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return tags.isEmpty() ? null : (tags.length() > 512 ? tags.substring(0, 512) : tags);
    }

    private LocalDateTime parsePublished(Document doc) {
        String raw = metaContent(doc, "meta[property=article:published_time]");
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw.trim()).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(raw.trim());
            } catch (Exception ignore) {
                return null;
            }
        }
    }
}
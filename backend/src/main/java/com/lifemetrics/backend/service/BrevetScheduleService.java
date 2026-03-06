package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.BrevetDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BrevetScheduleService {

    private static final String URL =
            "http://www.korearandonneurs.kr:8080/jsp/randonneurs/brevet_2026";

    private static final String[] REGIONS =
            {"서울/경기", "광주", "대구", "부산/제주", "천안/대전"};

    public List<BrevetDto> fetchSchedule() throws IOException {
        Document doc = Jsoup.connect(URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                           "AppleWebKit/537.36 (KHTML, like Gecko) " +
                           "Chrome/145.0.0.0 Safari/537.36")
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .header("Referer", "http://www.korearandonneurs.kr:8080/jsp/randonneurs/index/RL")
                .timeout(10000)
                .get();

        List<BrevetDto> result = new ArrayList<>();
        Elements rows = doc.select("table tbody tr");

        for (Element row : rows) {
            Element th = row.selectFirst("th");
            Elements tds = row.select("td");

            if (th == null || tds.isEmpty()) continue;

            String date = th.text().trim();

            // colspan=5 → 전국 단일 이벤트 (벡터, 플레쉬 등)
            Element firstTd = tds.get(0);
            String colspan = firstTd.attr("colspan");
            if (!colspan.isEmpty()) {
                String text = firstTd.text().trim();
                String link = firstTd.selectFirst("a") != null
                        ? firstTd.selectFirst("a").attr("href") : null;

                result.add(BrevetDto.builder()
                        .date(date)
                        .region("전국")
                        .name(text)
                        .distance(extractDistance(text))
                        .link(link)
                        .hasLink(link != null && !link.isEmpty())
                        .build());
                continue;
            }

            // 지역별 컬럼 파싱
            for (int i = 0; i < Math.min(tds.size(), REGIONS.length); i++) {
                Element td = tds.get(i);
                String text = td.text().trim();

                // 빈 셀 스킵
                if (text.isEmpty() || text.equals("\u00a0") || text.equals(" ")) continue;

                // 한 셀에 여러 브레베가 있는 경우 (예: 200(북) + 300(북))
                Elements anchors = td.select("a");
                Elements boldTexts = td.select("b");

                if (anchors.size() > 1) {
                    // 링크 있는 항목 여러 개
                    for (Element a : anchors) {
                        String aText = a.text().trim();
                        if (aText.isEmpty()) continue;
                        result.add(BrevetDto.builder()
                                .date(date)
                                .region(REGIONS[i])
                                .name(aText)
                                .distance(extractDistance(aText))
                                .link(a.attr("href"))
                                .hasLink(true)
                                .build());
                    }
                } else if (boldTexts.size() > 1 && anchors.isEmpty()) {
                    // 링크 없는 항목 여러 개 (준비중)
                    for (Element b : boldTexts) {
                        String bText = b.text().trim();
                        if (bText.isEmpty()) continue;
                        result.add(BrevetDto.builder()
                                .date(date)
                                .region(REGIONS[i])
                                .name(bText)
                                .distance(extractDistance(bText))
                                .link(null)
                                .hasLink(false)
                                .build());
                    }
                } else {
                    // 단일 항목
                    String link = anchors.isEmpty() ? null : anchors.first().attr("href");
                    result.add(BrevetDto.builder()
                            .date(date)
                            .region(REGIONS[i])
                            .name(text)
                            .distance(extractDistance(text))
                            .link(link)
                            .hasLink(link != null && !link.isEmpty())
                            .build());
                }
            }
        }

        log.info("브레베 일정 파싱 완료: {}건", result.size());
        return result;
    }

    /**
     * 텍스트에서 거리(km) 추출
     * "200 (서)" → "200"
     * "1200 S-H-S" → "1200"
     */
    private String extractDistance(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] parts = text.split("[^0-9]");
        for (String part : parts) {
            if (!part.isEmpty()) return part;
        }
        return "";
    }
}

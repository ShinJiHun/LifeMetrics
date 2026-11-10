package com.lifemetrics.backend.persona.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 쏘카 "핸들러 운행료 월간 명세서" PDF 에서 숫자를 뽑는다.
 * <p>
 * 양식이 고정된 1페이지 텍스트 PDF 라 AI 없이 정규식으로 충분하다(빠르고 결과가 항상 같음).
 * 읽은 결과는 파일명+수정시각 기준으로 메모리에 캐시한다.
 * <p>
 * 예시 텍스트:
 * <pre>
 * *귀속월 : 2026  년 10월
 * 개시일 2026-10-01
 * 종료일 2026-10-31
 * 용역제공일수 25일 (58 회)
 * 용역구분 탁송
 * 지급금액 571,500원
 * 2026년 11월 10일          ← 발급일
 * </pre>
 */
@Component
public class HandlerSettlementParser {

    private static final Pattern PERIOD = Pattern.compile("귀속월\\s*:\\s*(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월");
    private static final Pattern START = Pattern.compile("개시일\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern END = Pattern.compile("종료일\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern DAYS = Pattern.compile("용역제공일수\\s*(\\d+)\\s*일\\s*\\(\\s*(\\d+)\\s*회\\s*\\)");
    private static final Pattern TYPE = Pattern.compile("용역구분\\s*(\\S+)");
    private static final Pattern AMOUNT = Pattern.compile("지급금액\\s*([\\d,]+)\\s*원");
    private static final Pattern ISSUED = Pattern.compile("(?m)^\\s*(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일\\s*$");

    /** PDF 에서 읽은 값. 못 읽은 항목은 null. */
    public record Parsed(
            String periodMonth,   // 귀속월 "YYYY-MM"
            LocalDate startDate,
            LocalDate endDate,
            Integer workDays,     // 용역제공일수
            Integer trips,        // 회
            String serviceType,   // 용역구분 (탁송 등)
            Long grossAmount,     // 지급금액 (세전으로 간주)
            LocalDate issuedDate  // 명세서 발급일
    ) {
        public boolean ok() {
            return periodMonth != null && grossAmount != null;
        }
    }

    private record CacheEntry(FileTime mtime, Parsed parsed) {}

    private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();

    public Parsed parse(Path pdf) {
        try {
            FileTime mtime = Files.getLastModifiedTime(pdf);
            CacheEntry hit = cache.get(pdf);
            if (hit != null && hit.mtime().equals(mtime)) return hit.parsed();

            Parsed parsed = parseText(extractText(Files.readAllBytes(pdf)));
            cache.put(pdf, new CacheEntry(mtime, parsed));
            return parsed;
        } catch (IOException | RuntimeException e) {
            return parseText("");
        }
    }

    public Parsed parse(byte[] pdfBytes) {
        try {
            return parseText(extractText(pdfBytes));
        } catch (IOException | RuntimeException e) {
            return parseText("");
        }
    }

    public void evict(Path pdf) {
        cache.remove(pdf);
    }

    private static String extractText(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    static Parsed parseText(String text) {
        String periodMonth = null;
        Matcher m = PERIOD.matcher(text);
        if (m.find()) {
            int month = Integer.parseInt(m.group(2));
            if (month >= 1 && month <= 12) periodMonth = m.group(1) + "-" + String.format("%02d", month);
        }

        Integer days = null, trips = null;
        m = DAYS.matcher(text);
        if (m.find()) {
            days = Integer.parseInt(m.group(1));
            trips = Integer.parseInt(m.group(2));
        }

        Long amount = null;
        m = AMOUNT.matcher(text);
        if (m.find()) amount = Long.parseLong(m.group(1).replace(",", ""));

        LocalDate issued = null;
        m = ISSUED.matcher(text);
        if (m.find()) {
            try {
                issued = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            } catch (RuntimeException ignored) {
                // 날짜가 이상하면 비워 둔다
            }
        }

        return new Parsed(periodMonth, date(START, text), date(END, text), days, trips,
                group(TYPE, text), amount, issued);
    }

    private static LocalDate date(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group(1));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String group(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}

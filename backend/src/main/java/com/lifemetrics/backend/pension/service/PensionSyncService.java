package com.lifemetrics.backend.pension.service;

import com.lifemetrics.backend.pension.entity.PensionNumber;
import com.lifemetrics.backend.pension.repository.PensionRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PensionSyncService {

    @Autowired
    private PensionRepository pensionRepository;

    @Autowired
    private RestTemplate restTemplate;

    // 연금복권 URL
    private static final String PENSION_LOTTERY_URL = "https://www.dhlottery.co.kr/pt720/result";

    /**
     * 연금복권 당첨번호 동기화
     * dhlottery.co.kr/pt720/result에서 최신 데이터 크롤링
     */
    public void syncAll() {
        try {
            log.info("🎰 Starting pension lottery sync from: {}", PENSION_LOTTERY_URL);

            Document doc = fetchDocument(PENSION_LOTTERY_URL);
            if (doc == null) {
                log.error("❌ Failed to fetch pension lottery page");
                return;
            }

            // swiper-slide 클래스를 가진 모든 div 찾기
            Elements slides = doc.select("div.swiper-slide");
            log.info("📊 Found {} pension lottery slides", slides.size());

            int successCount = 0;
            int skipCount = 0;

            for (Element slide : slides) {
                try {
                    // 회차번호 추출 (span.psltEpsd)
                    Element roundElem = slide.selectFirst("span.psltEpsd");
                    if (roundElem == null) {
                        skipCount++;
                        continue;
                    }

                    String roundText = roundElem.text().trim();
                    Integer roundNumber = parseRoundNumber(roundText);

                    if (roundNumber == null) {
                        log.warn("⚠️ Failed to parse round number: {}", roundText);
                        skipCount++;
                        continue;
                    }

                    // 이미 저장된 회차인지 확인
                    if (pensionRepository.findByRoundNumber(roundNumber).isPresent()) {
                        log.debug("⏭️ Round {} already exists, skipping", roundNumber);
                        skipCount++;
                        continue;
                    }

                    // 당첨 조 추출 (1-5)
                    Integer winningJo = extractWinningJo(slide);
                    if (winningJo == null) {
                        log.warn("⚠️ Failed to extract winning jo for round {}", roundNumber);
                        skipCount++;
                        continue;
                    }

                    // 당첨번호 추출 (6자리)
                    String winningNumbers = extractWinningNumbers(slide);
                    if (winningNumbers == null || winningNumbers.isEmpty()) {
                        log.warn("⚠️ Failed to extract winning numbers for round {}", roundNumber);
                        skipCount++;
                        continue;
                    }

                    // 보너스번호 추출
                    String bonusNumber = extractBonusNumber(slide);
                    if (bonusNumber == null || bonusNumber.isEmpty()) {
                        log.warn("⚠️ Failed to extract bonus number for round {}", roundNumber);
                        skipCount++;
                        continue;
                    }

                    // 당첨일자 추출
                    String drawDate = extractDrawDate(slide);

                    // 당첨금액 추출
                    Long firstPrizeAmount = extractPrizeAmount(slide);

                    // PensionNumber 객체 생성 및 저장
                    PensionNumber pensionNumber = PensionNumber.builder()
                            .roundNumber(roundNumber)
                            .winningJo(winningJo)
                            .winningNumbers(winningNumbers)
                            .bonusNumber(bonusNumber)
                            .drawDate(drawDate)
                            .firstPrizeAmount(firstPrizeAmount)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    pensionRepository.save(pensionNumber);
                    log.info("✅ Saved pension lottery round {}: jo={}, numbers={}, bonus={}", roundNumber, winningJo, winningNumbers, bonusNumber);
                    successCount++;

                } catch (Exception e) {
                    log.error("❌ Error processing pension lottery slide: {}", e.getMessage(), e);
                    skipCount++;
                }
            }

            log.info("📈 Pension lottery sync completed. Success: {}, Skipped: {}", successCount, skipCount);

        } catch (Exception e) {
            log.error("❌ Fatal error during pension lottery sync: {}", e.getMessage(), e);
        }
    }

    /**
     * dhlottery.co.kr에서 HTML 문서 가져오기
     * User-Agent 헤더 필요 (봇 차단 회피)
     */
    private Document fetchDocument(String url) {
        try {
            String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .header("Referer", "https://www.dhlottery.co.kr/")
                    .timeout(10000)  // 10초 타임아웃
                    .get();

            log.debug("✅ Successfully fetched document from {}", url);
            return doc;

        } catch (IOException e) {
            log.error("❌ Failed to fetch document: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 모든 회차 역순 백필 (1회부터 최신까지)
     * 최신 회차부터 역순으로 순회하며 데이터 수집
     */
    public void backfillAllRounds() {
        try {
            log.info("🎰 Starting backfill of all pension lottery rounds...");

            // 최신 회차 찾기
            Optional<PensionNumber> latest = pensionRepository.findLatestRound();
            int latestRound = latest.map(PensionNumber::getRoundNumber).orElse(1);

            log.info("📊 Latest round: {}, will backfill from this to round 1", latestRound);

            int successCount = 0;
            int skipCount = 0;
            int failCount = 0;

            // 최신 회차부터 1회까지 역순 순회
            for (int roundNum = latestRound; roundNum >= 1; roundNum--) {
                try {
                    // 이미 저장된 회차인지 확인
                    if (pensionRepository.findByRoundNumber(roundNum).isPresent()) {
                        log.debug("⏭️ Round {} already exists, skipping", roundNum);
                        skipCount++;
                    } else {
                        // 해당 회차의 상세 페이지 크롤링
                        String roundUrl = PENSION_LOTTERY_URL + "?drwtNo=" + roundNum;
                        Document doc = fetchDocument(roundUrl);

                        if (doc == null) {
                            log.warn("⚠️ Failed to fetch round {}", roundNum);
                            failCount++;
                        } else {
                            // 데이터 추출 및 저장
                            Element slide = doc.selectFirst("div.swiper-slide");
                            if (slide != null) {
                                Integer winningJo = extractWinningJo(slide);
                                String winningNumbers = extractWinningNumbers(slide);
                                String bonusNumber = extractBonusNumber(slide);
                                String drawDate = extractDrawDate(slide);
                                Long firstPrizeAmount = extractPrizeAmount(slide);

                                if (winningJo != null && winningNumbers != null && bonusNumber != null) {
                                    PensionNumber pensionNumber = PensionNumber.builder()
                                            .roundNumber(roundNum)
                                            .winningJo(winningJo)
                                            .winningNumbers(winningNumbers)
                                            .bonusNumber(bonusNumber)
                                            .drawDate(drawDate)
                                            .firstPrizeAmount(firstPrizeAmount)
                                            .createdAt(LocalDateTime.now())
                                            .updatedAt(LocalDateTime.now())
                                            .build();

                                    pensionRepository.save(pensionNumber);
                                    log.info("✅ Backfilled round {}: jo={}, numbers={}, bonus={}", roundNum, winningJo, winningNumbers, bonusNumber);
                                    successCount++;
                                } else {
                                    log.warn("⚠️ Failed to extract data for round {}", roundNum);
                                    failCount++;
                                }
                            } else {
                                failCount++;
                            }
                        }
                    }

                    // 5회마다 100ms 지연 (봇 차단 회피)
                    if (roundNum % 5 == 0) {
                        Thread.sleep(100);
                    }

                    // 10회마다 진행상황 로그
                    if (roundNum % 10 == 0) {
                        log.info("📈 Backfill progress: Round {}, Success: {}, Skip: {}, Fail: {}", roundNum, successCount, skipCount, failCount);
                    }

                } catch (InterruptedException e) {
                    log.warn("⚠️ Backfill interrupted at round {}", roundNum);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("❌ Error processing round {}: {}", roundNum, e.getMessage());
                    failCount++;
                }
            }

            log.info("✅ Backfill completed. Success: {}, Skip: {}, Fail: {}", successCount, skipCount, failCount);

        } catch (Exception e) {
            log.error("❌ Fatal error during backfill: {}", e.getMessage(), e);
        }
    }

    /**
     * 회차번호 파싱 (예: "640회" → 640)
     */
    private Integer parseRoundNumber(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // 숫자만 추출 (정규표현식: 숫자 한 개 이상)
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("⚠️ Failed to parse round number from: {}", text);
                return null;
            }
        }

        return null;
    }

    /**
     * 당첨 조 추출 (1-5)
     */
    private Integer extractWinningJo(Element slide) {
        try {
            // 조 번호는 보통 특정 패턴의 텍스트로 표현됨
            Elements allText = slide.select("*");
            Pattern joPattern = Pattern.compile("[조|group]\\s*[=:]?\\s*([1-5])");

            for (Element elem : allText) {
                Matcher matcher = joPattern.matcher(elem.text());
                if (matcher.find()) {
                    try {
                        return Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException e) {
                        log.debug("⚠️ Failed to parse jo: {}", matcher.group(1));
                    }
                }
            }

            // 대체 방법: 페이지 전체에서 찾기
            String pageText = slide.text();
            Pattern altPattern = Pattern.compile("[1-5]\\s*조");
            Matcher altMatcher = altPattern.matcher(pageText);
            if (altMatcher.find()) {
                String match = altMatcher.group();
                return Integer.parseInt(match.replaceAll("[^0-9]", ""));
            }

            return null;

        } catch (Exception e) {
            log.warn("⚠️ Error extracting winning jo: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 당첨번호 추출 (6자리, 예: "123456")
     * swiper-slide 내의 6자리 숫자 찾기
     */
    private String extractWinningNumbers(Element slide) {
        try {
            // 여러 가능한 선택자들을 시도
            // 방법 1: span.num 클래스 찾기
            Elements numberElements = slide.select("span.num");

            if (numberElements.isEmpty()) {
                // 방법 2: 다른 패턴 찾기
                numberElements = slide.select("span[class*='num']");
            }

            if (numberElements.isEmpty()) {
                // 방법 3: 숫자로 보이는 텍스트 모두 찾기
                Elements allSpans = slide.select("span");
                List<String> numbers = new ArrayList<>();

                for (Element span : allSpans) {
                    String text = span.text().trim();
                    // 6자리 숫자만 취하기
                    if (text.matches("\\d{6}")) {
                        return text;  // 첫 번째 6자리 발견 시 반환
                    }
                }
            }

            // numberElements가 있으면 텍스트 추출
            for (Element elem : numberElements) {
                String text = elem.text().trim();
                if (text.matches("\\d{6}")) {
                    return text;
                }
            }

            return null;

        } catch (Exception e) {
            log.warn("⚠️ Error extracting winning numbers: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 보너스번호 추출 (2자리, 예: "45")
     */
    private String extractBonusNumber(Element slide) {
        try {
            // 보너스 번호는 보통 "보너스", "bonus" 레이블 다음에 위치
            Elements allText = slide.select("*");

            for (int i = 0; i < allText.size(); i++) {
                Element elem = allText.get(i);
                String text = elem.text().toLowerCase();

                if (text.contains("bonus") || text.contains("보너스")) {
                    // 다음 요소에서 숫자 찾기
                    if (i + 1 < allText.size()) {
                        String nextText = allText.get(i + 1).text().trim();
                        if (nextText.matches("\\d{2}")) {
                            return nextText;
                        }
                    }

                    // 현재 요소에서 2자리 숫자 추출
                    Pattern bonusPattern = Pattern.compile("\\d{2}");
                    Matcher matcher = bonusPattern.matcher(text);
                    if (matcher.find()) {
                        return matcher.group();
                    }
                }
            }

            // 대체 방법: 페이지 전체에서 2자리 숫자 모두 찾기
            Elements allSpans = slide.select("span");
            for (Element span : allSpans) {
                String text = span.text().trim();
                if (text.matches("\\d{2}")) {
                    // 마지막 2자리 숫자가 보너스일 가능성 높음
                    return text;
                }
            }

            return null;

        } catch (Exception e) {
            log.warn("⚠️ Error extracting bonus number: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 당첨일자 추출 (예: "2024년 5월 4일")
     */
    private String extractDrawDate(Element slide) {
        try {
            // 날짜 정보는 보통 특정 클래스에 담겨있음
            Element dateElem = slide.selectFirst("span.psltDate, span[class*='date'], p[class*='date']");

            if (dateElem != null) {
                return dateElem.text().trim();
            }

            // 대체 방법: 날짜 패턴으로 찾기
            Elements allText = slide.select("*");
            Pattern datePattern = Pattern.compile("\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일");

            for (Element elem : allText) {
                Matcher matcher = datePattern.matcher(elem.text());
                if (matcher.find()) {
                    return matcher.group();
                }
            }

            return null;

        } catch (Exception e) {
            log.warn("⚠️ Error extracting draw date: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 1등 당첨금액 추출
     */
    private Long extractPrizeAmount(Element slide) {
        try {
            // 당첨금 정보를 찾기
            Elements prizeElements = slide.select("span[class*='prize'], span[class*='amount']");

            if (prizeElements.isEmpty()) {
                return null;
            }

            for (Element elem : prizeElements) {
                String text = elem.text().replaceAll("[^0-9]", "");  // 숫자만 추출

                if (!text.isEmpty()) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException e) {
                        log.debug("⚠️ Failed to parse prize amount: {}", text);
                    }
                }
            }

            return null;

        } catch (Exception e) {
            log.warn("⚠️ Error extracting prize amount: {}", e.getMessage());
            return null;
        }
    }
}

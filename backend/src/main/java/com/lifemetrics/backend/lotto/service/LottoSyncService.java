package com.lifemetrics.backend.lotto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.lotto.dto.LottoSyncResponse;
import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import com.lifemetrics.backend.lotto.repository.LottoNumberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 동행복권 사이트(dhlottery.co.kr)의 회차별 당첨번호 조회 API에서 값을 가져와
 * lotto_number 를 채운다.
 *
 * 엔드포인트: GET /lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd={round}
 * 한 번 호출에 요청한 회차 기준으로 여러 회차(관찰상 최대 10개)를 한꺼번에 반환한다.
 * "center" 파라미터의 정확한 윈도우 규칙은 공개돼있지 않아, 응답에 포함된 회차 중
 * 가장 큰 값 다음 회차로 다시 요청하는 방식으로 안전하게 전진하며 채운다.
 *
 * 관리자만 호출 가능 (AdminWriteFilter 가 모든 쓰기 요청을 자동으로 보호함).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
public class LottoSyncService {

    private final LottoNumberRepository numberRepo;
    private final ObjectMapper objectMapper;

    private static final String API_URL =
            "https://www.dhlottery.co.kr/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=";

    /** 1회차 추첨일 (2002-12-07, 매주 토요일 추첨). */
    private static final LocalDate FIRST_DRAW_DATE = LocalDate.of(2002, 12, 7);
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    public LottoSyncResponse syncAll() {
        int fromRound = numberRepo.findMaxRound() == null ? 1 : numberRepo.findMaxRound() + 1;
        int latestPossible = estimateLatestRound();

        int synced = 0;
        int round = fromRound;
        int safetyLoops = 0;

        while (round <= latestPossible + 5 && safetyLoops < 500) {
            safetyLoops++;
            List<JsonNode> items = fetchWindow(round);
            if (items.isEmpty()) break;

            int maxSeen = round - 1;
            for (JsonNode item : items) {
                int r = item.path("ltEpsd").asInt(-1);
                if (r < 1) continue;
                maxSeen = Math.max(maxSeen, r);
                if (r < fromRound) continue; // 이미 저장된 회차는 건너뜀
                numberRepo.save(toEntity(item));
                synced++;
            }

            if (maxSeen < round) break; // 진행이 없으면 무한루프 방지
            round = maxSeen + 1;
        }

        int toRound = round - 1;
        String message = synced == 0
                ? "새로 추가된 회차가 없습니다 (최신 상태)."
                : synced + "개 회차(" + fromRound + "~" + toRound + ") 저장 완료";
        log.info("로또 당첨번호 동기화: {}", message);
        return new LottoSyncResponse(fromRound, toRound, synced, message);
    }

    private int estimateLatestRound() {
        long weeksSinceFirst = ChronoUnit.WEEKS.between(FIRST_DRAW_DATE, LocalDate.now());
        return (int) weeksSinceFirst + 1;
    }

    /** 주어진 회차를 기준으로 dhlottery API를 호출해 응답의 data.list 배열을 반환한다. */
    private List<JsonNode> fetchWindow(int round) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json, text/javascript, */*; q=0.01");
            headers.set("Referer", "https://www.dhlottery.co.kr/lt645/result");
            headers.set("X-Requested-With", "XMLHttpRequest");

            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL + round, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode list = root.path("data").path("list");

            List<JsonNode> result = new ArrayList<>();
            if (list.isArray()) {
                list.forEach(result::add);
            }
            return result;
        } catch (Exception e) {
            log.warn("회차 {} 기준 조회 실패: {}", round, e.getMessage());
            return List.of();
        }
    }

    private LottoNumberEntity toEntity(JsonNode d) {
        LottoNumberEntity e = new LottoNumberEntity();
        e.setRound(d.get("ltEpsd").asInt());
        e.setN1(d.get("tm1WnNo").asInt());
        e.setN2(d.get("tm2WnNo").asInt());
        e.setN3(d.get("tm3WnNo").asInt());
        e.setN4(d.get("tm4WnNo").asInt());
        e.setN5(d.get("tm5WnNo").asInt());
        e.setN6(d.get("tm6WnNo").asInt());
        e.setBonus(d.get("bnsWnNo").asInt());
        e.setDrawDate(LocalDate.parse(d.get("ltRflYmd").asText(), YMD));
        return e;
    }
}

package com.lifemetrics.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 방문자 공개 챗(AI 호출 비용 발생) 하루 사용량 제한. IP 별 + 전체 합계. 날짜(한국 시각)가 바뀌면 초기화.
 * 서버 메모리에만 두므로 재시작하면 초기화된다(남용 방지용이라 충분). 관리자는 호출하는 쪽에서 제외한다.
 */
@Component
public class PublicChatRateLimiter {

    private final int perIpPerDay;
    private final int totalPerDay;
    private final Clock clock;

    private LocalDate day;
    private final Map<String, AtomicInteger> perIp = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    public PublicChatRateLimiter(@Value("${ai.public-chat.per-ip-per-day:30}") int perIpPerDay,
                                 @Value("${ai.public-chat.total-per-day:300}") int totalPerDay) {
        this(perIpPerDay, totalPerDay, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    PublicChatRateLimiter(int perIpPerDay, int totalPerDay, Clock clock) {
        this.perIpPerDay = perIpPerDay;
        this.totalPerDay = totalPerDay;
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    public enum Result { OK, IP_LIMIT, TOTAL_LIMIT }

    /** 한 번 쓸 수 있으면 세고 OK. 넘었으면 세지 않고 이유를 돌려준다. */
    public synchronized Result tryAcquire(String ip) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            perIp.clear();
            total.set(0);
        }
        if (total.get() >= totalPerDay) return Result.TOTAL_LIMIT;
        AtomicInteger n = perIp.computeIfAbsent(ip == null ? "unknown" : ip, k -> new AtomicInteger());
        if (n.get() >= perIpPerDay) return Result.IP_LIMIT;
        n.incrementAndGet();
        total.incrementAndGet();
        return Result.OK;
    }

    public static String message(Result r) {
        return r == Result.IP_LIMIT
                ? "오늘 질문할 수 있는 횟수를 다 썼어요. 내일 다시 이용해 주세요."
                : "오늘은 이용량이 많아 잠시 쉬고 있어요. 내일 다시 이용해 주세요.";
    }
}

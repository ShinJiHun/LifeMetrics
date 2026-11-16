package com.lifemetrics.backend.persona.worklog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매주 금요일 21:00 경력기술서 초안을 만든다(지난 초안 이후 보고서가 바뀐 경우만). 반영은 하지 않는다 — 관리자가 화면에서 승인.
 * dev 서버는 app.scheduling.enabled=false 로 꺼진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class WorkLogCareerScheduler {

    private final WorkLogCareerService careerService;

    @Scheduled(cron = "0 0 21 * * FRI", zone = "Asia/Seoul")
    public void weeklyDraft() {
        try {
            careerService.generateIfChanged().ifPresentOrElse(
                    d -> log.info("경력기술서 초안 생성 id={}", d.id()),
                    () -> log.info("경력기술서 초안: 바뀐 보고서 없음, 건너뜀"));
        } catch (Exception e) {
            log.warn("경력기술서 초안 배치 실패: {}", e.getMessage());
        }
    }
}

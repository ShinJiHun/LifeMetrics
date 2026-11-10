package com.lifemetrics.backend.ddareungi;

import com.lifemetrics.backend.notify.SlackNotifier;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 따릉이 캡처 처리를 백그라운드 작업으로 돌린다.
 * <p>
 * 이미지 인식(Claude) + 경로 탐색이 수십 초 걸려, 한 HTTP 요청으로 기다리면 모바일 회선·프록시가
 * 중간에 연결을 끊는다(nginx 499 → 브라우저 "Failed to fetch"). 그래서 업로드는 작업 id 만 바로
 * 돌려주고, 화면이 {@link #get(String)} 으로 결과를 폴링한다.
 * <p>
 * 작업은 메모리에만 두고 1시간 뒤 정리한다(재시작하면 사라짐 — 다시 올리면 됨).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DdareungiJobService {

    private static final Duration TTL = Duration.ofHours(1);

    private final DdareungiImportService importService;
    private final SlackNotifier slackNotifier;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ddareungi-job");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    /** 요청이 끝나도 남도록 업로드 파일을 바이트로 복사해 둔 것. */
    public record ImageInput(String filename, String contentType, byte[] bytes) {}

    public record Job(String id, String status, int total, int done,
                      List<DdareungiImportService.FileResult> results, String message, Instant createdAt) {
        Job with(String status, int done, List<DdareungiImportService.FileResult> results, String message) {
            return new Job(id, status, total, done, results, message, createdAt);
        }
    }

    public Job submit(List<ImageInput> images, Long userId, boolean dryRun) {
        cleanup();
        String id = UUID.randomUUID().toString();
        Job job = new Job(id, "running", images.size(), 0, List.of(), null, Instant.now());
        jobs.put(id, job);

        executor.submit(() -> {
            List<DdareungiImportService.FileResult> results = new ArrayList<>();
            try {
                for (ImageInput img : images) {
                    results.add(importService.importImage(img.filename(), img.contentType(), img.bytes(), userId, dryRun));
                    jobs.computeIfPresent(id, (k, j) -> j.with("running", results.size(), List.copyOf(results), null));
                }
                if (!dryRun) {
                    long saved = results.stream().flatMap(r -> r.rides().stream())
                            .filter(r -> "saved".equals(r.status())).count();
                    if (saved > 0) slackNotifier.send("🚲 *따릉이 기록 " + saved + "건 추가*");
                }
                jobs.computeIfPresent(id, (k, j) -> j.with("done", results.size(), List.copyOf(results), null));
            } catch (Exception e) {
                log.error("따릉이 작업 실패 {}", id, e);
                jobs.computeIfPresent(id, (k, j) -> j.with("error", results.size(), List.copyOf(results), e.getMessage()));
            }
        });
        return job;
    }

    public Optional<Job> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(TTL);
        jobs.values().removeIf(j -> j.createdAt().isBefore(cutoff));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}

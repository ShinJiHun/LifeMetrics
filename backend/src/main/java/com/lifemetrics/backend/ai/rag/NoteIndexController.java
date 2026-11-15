package com.lifemetrics.backend.ai.rag;

import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RAG 색인 수동 실행·상태. 백그라운드로 돌린다(임베딩을 켜면 수백 번 호출해 1~2분 걸림).
 * 서버가 뜰 때 한 번 색인한다(키워드 전용이면 외부 호출 없이 몇 초).
 * POST 는 AdminWriteFilter 가 관리자만 허용, GET 은 여기서 확인한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/athlete/notes")
@RequiredArgsConstructor
public class NoteIndexController {

    static final long USER_ID = 1L;

    private final NoteIndexService indexService;
    private final GeminiEmbeddingClient embedder;
    private final AdminWriteFilter adminWriteFilter;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile NoteIndexService.IndexResult lastResult;
    private volatile LocalDateTime lastFinishedAt;
    private volatile String lastError;

    @PostMapping("/reindex")
    public ResponseEntity<?> reindex() {
        if (!start()) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "이미 색인 중입니다."));
        return ResponseEntity.accepted().body(Map.of("message", "색인을 시작했습니다. 상태는 GET /api/athlete/notes/status"));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("running", running.get());
        s.put("lastResult", lastResult);
        s.put("lastFinishedAt", lastFinishedAt);
        s.put("lastError", lastError);
        s.put("searchMode", indexService.mode());
        s.put("embeddingModel", embedder.isConfigured() ? embedder.model() : null);
        return ResponseEntity.ok(s);
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void indexOnStartup() {
        start();
    }

    /** 백그라운드 색인 시작. 이미 돌고 있으면 false. 스케줄러도 이걸 쓴다. */
    boolean start() {
        if (!running.compareAndSet(false, true)) return false;
        CompletableFuture.runAsync(() -> {
            try {
                lastResult = indexService.reindex(USER_ID);
                lastError = null;
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("RAG 색인 실패: {}", e.getMessage());
            } finally {
                lastFinishedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
                running.set(false);
            }
        });
        return true;
    }
}

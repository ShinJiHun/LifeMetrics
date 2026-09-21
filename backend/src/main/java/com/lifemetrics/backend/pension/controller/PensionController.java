package com.lifemetrics.backend.pension.controller;

import com.lifemetrics.backend.pension.entity.PensionNumber;
import com.lifemetrics.backend.pension.repository.PensionRepository;
import com.lifemetrics.backend.pension.service.PensionSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/pension")
public class PensionController {

    @Autowired
    private PensionRepository pensionRepository;

    @Autowired
    private PensionSyncService pensionSyncService;

    /**
     * 모든 회차 목록 조회 (회차번호, 날짜)
     * GET /api/pension/rounds
     */
    @GetMapping("/rounds")
    public ResponseEntity<?> getAllRounds() {
        try {
            List<PensionNumber> allData = pensionRepository.findAll();
            List<Map<String, Object>> rounds = new ArrayList<>();
            
            for (PensionNumber p : allData) {
                Map<String, Object> round = new HashMap<>();
                round.put("roundNumber", p.getRoundNumber());
                round.put("drawDate", p.getDrawDate() != null ? p.getDrawDate() : "");
                rounds.add(round);
            }
            
            // 역순 정렬 (최신부터)
            rounds.sort((a, b) -> ((Integer)b.get("roundNumber")).compareTo((Integer)a.get("roundNumber")));
            
            // 최신 20개만 반환 (성능 개선)
            return ResponseEntity.ok(rounds.stream().limit(20).toList());
        } catch (Exception e) {
            log.error("❌ Error fetching rounds: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 최신 회차 조회
     * GET /api/pension/latest
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestRound() {
        try {
            Optional<PensionNumber> latest = pensionRepository.findLatestRound();

            if (latest.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(latest.get());
        } catch (Exception e) {
            log.error("❌ Error fetching latest round: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 특정 회차 조회
     * GET /api/pension/{roundNumber}
     */
    @GetMapping("/{roundNumber}")
    public ResponseEntity<?> getRoundByNumber(@PathVariable Integer roundNumber) {
        try {
            Optional<PensionNumber> pension = pensionRepository.findByRoundNumber(roundNumber);

            if (pension.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(pension.get());
        } catch (Exception e) {
            log.error("❌ Error fetching round {}: {}", roundNumber, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 최근 N개 회차 조회
     * GET /api/pension/recent?count=10
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentRounds(
            @RequestParam(value = "count", defaultValue = "10") Integer count) {
        try {
            List<PensionNumber> recentRounds = pensionRepository.findRecentRounds(
                Math.max(1, pensionRepository.findLatestRound()
                    .map(p -> p.getRoundNumber() - count + 1)
                    .orElse(1))
            );

            return ResponseEntity.ok(recentRounds);
        } catch (Exception e) {
            log.error("❌ Error fetching recent rounds: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 모든 회차 조회 (관리자용, /api/pension/all)
     * GET /api/pension/all
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllRoundsData() {
        try {
            List<PensionNumber> allRounds = pensionRepository.findAll();
            return ResponseEntity.ok(allRounds);
        } catch (Exception e) {
            log.error("❌ Error fetching all rounds: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 통계 조회
     * GET /api/pension/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            long totalRounds = pensionRepository.count();
            Optional<PensionNumber> latestRound = pensionRepository.findLatestRound();
            Optional<PensionNumber> oldestRound = pensionRepository.findOldestRound();

            long totalPrizeAmount = pensionRepository.findAll().stream()
                    .mapToLong(p -> p.getFirstPrizeAmount() != null ? p.getFirstPrizeAmount() : 0)
                    .sum();

            Map<String, Object> stats = new HashMap<>();
            stats.put("success", true);
            stats.put("totalRounds", totalRounds);
            stats.put("latestRound", latestRound.map(PensionNumber::getRoundNumber).orElse(0));
            stats.put("oldestRound", oldestRound.map(PensionNumber::getRoundNumber).orElse(0));
            stats.put("totalPrizeAmount", totalPrizeAmount);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("❌ Error fetching stats: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 최신 데이터 동기화 (관리자용)
     * POST /api/pension/sync
     * 당첨번호 사이트에서 최신 회차만 수집
     * (AdminWriteFilter에 의해 자동으로 관리자 전용)
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncLatest() {
        try {
            log.info("🎰 Starting manual pension lottery sync...");
            pensionSyncService.syncAll();
            
            Optional<PensionNumber> latest = pensionRepository.findLatestRound();
            long totalRounds = pensionRepository.count();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Pension lottery sync completed");
            response.put("latestRound", latest.map(PensionNumber::getRoundNumber).orElse(0));
            response.put("totalRounds", totalRounds);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error during pension sync: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Sync failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 과거 모든 회차 백필 (관리자용)
     * POST /api/pension/backfill
     * 1회부터 최신까지 모든 데이터 수집 (오래 걸림)
     * (AdminWriteFilter에 의해 자동으로 관리자 전용)
     */
    @PostMapping("/backfill")
    public ResponseEntity<?> backfillAll() {
        try {
            log.info("🎰 Starting pension lottery backfill (all rounds)...");
            
            // 백그라운드에서 실행 (비동기)
            new Thread(() -> {
                try {
                    pensionSyncService.backfillAllRounds();
                    log.info("✅ Backfill completed");
                } catch (Exception e) {
                    log.error("❌ Backfill failed: {}", e.getMessage());
                }
            }).start();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Backfill started in background (this may take several minutes)");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error starting backfill: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Backfill start failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}

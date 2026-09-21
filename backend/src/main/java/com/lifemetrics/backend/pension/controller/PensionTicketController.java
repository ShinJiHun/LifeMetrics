package com.lifemetrics.backend.pension.controller;

import com.lifemetrics.backend.pension.dto.PensionTicketDto;
import com.lifemetrics.backend.pension.dto.PensionTicketResponse;
import com.lifemetrics.backend.pension.entity.PensionTicketEntity;
import com.lifemetrics.backend.pension.repository.PensionTicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/pension/ticket")
public class PensionTicketController {

    @Autowired
    private PensionTicketRepository pensionTicketRepository;

    /**
     * 연금복권 번호 등록 (직접 입력)
     * POST /api/pension/ticket
     * Body: { round, jo, numbers: [n1, n2, n3, n4, n5, n6], purchasedAt }
     */
    @PostMapping
    public ResponseEntity<?> registerTicket(@RequestBody Map<String, Object> request) {
        try {
            Integer round = (Integer) request.get("round");
            Integer jo = (Integer) request.get("jo");
            List<Integer> numbersList = (List<Integer>) request.get("numbers");
            String purchasedAtStr = (String) request.get("purchasedAt");

            if (round == null || jo == null || numbersList == null || numbersList.size() != 6) {
                throw new IllegalArgumentException("회차, 당첨조, 6개 번호가 모두 필요합니다");
            }

            int[] numbers = numbersList.stream().mapToInt(Integer::intValue).toArray();

            // 중복 확인
            Optional<PensionTicketEntity> duplicate = pensionTicketRepository.findDuplicate(
                    round, numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5], jo
            );

            if (duplicate.isPresent()) {
                return ResponseEntity.ok(
                        PensionTicketResponse.builder()
                                .success(true)
                                .message("이미 등록된 동일 번호입니다 (중복 무시됨)")
                                .savedCount(0)
                                .duplicateCount(1)
                                .build()
                );
            }

            // 새로운 티켓 저장
            PensionTicketEntity ticket = PensionTicketEntity.builder()
                    .round(round)
                    .jo(jo)
                    .n1(numbers[0])
                    .n2(numbers[1])
                    .n3(numbers[2])
                    .n4(numbers[3])
                    .n5(numbers[4])
                    .n6(numbers[5])
                    .source("MANUAL")
                    .purchasedAt(purchasedAtStr != null ? LocalDate.parse(purchasedAtStr) : LocalDate.now())
                    .build();

            PensionTicketEntity saved = pensionTicketRepository.save(ticket);

            PensionTicketDto dto = PensionTicketDto.from(saved);
            List<PensionTicketDto> tickets = new ArrayList<>();
            tickets.add(dto);

            return ResponseEntity.ok(
                    PensionTicketResponse.builder()
                            .success(true)
                            .message("1개 연금복권이 등록되었습니다")
                            .tickets(tickets)
                            .savedCount(1)
                            .duplicateCount(0)
                            .build()
            );
        } catch (Exception e) {
            log.error("❌ Error registering pension ticket: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    PensionTicketResponse.builder()
                            .success(false)
                            .message("오류: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * 모든 연금복권 티켓 조회
     * GET /api/pension/ticket
     */
    @GetMapping
    public ResponseEntity<?> getAllTickets() {
        try {
            List<PensionTicketEntity> entities = pensionTicketRepository.findAll();
            List<PensionTicketDto> tickets = new ArrayList<>();
            
            for (PensionTicketEntity entity : entities) {
                tickets.add(PensionTicketDto.from(entity));
            }

            // 최신순으로 정렬
            tickets.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            
            // 최신 50개만 반환 (성능 개선)
            return ResponseEntity.ok(tickets.stream().limit(50).toList());
        } catch (Exception e) {
            log.error("❌ Error fetching tickets: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "오류: " + e.getMessage())
            );
        }
    }

    /**
     * 최근 N개 티켓 조회
     * GET /api/pension/ticket/recent?count=10
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentTickets(
            @RequestParam(value = "count", defaultValue = "20") Integer count) {
        try {
            List<PensionTicketEntity> entities = pensionTicketRepository.findRecentTickets(count);
            List<PensionTicketDto> tickets = new ArrayList<>();
            
            for (PensionTicketEntity entity : entities) {
                tickets.add(PensionTicketDto.from(entity));
            }

            return ResponseEntity.ok(tickets);
        } catch (Exception e) {
            log.error("❌ Error fetching recent tickets: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "오류: " + e.getMessage())
            );
        }
    }

    /**
     * 특정 회차의 티켓 조회
     * GET /api/pension/ticket/round/{roundNumber}
     */
    @GetMapping("/round/{roundNumber}")
    public ResponseEntity<?> getTicketsByRound(@PathVariable Integer roundNumber) {
        try {
            List<PensionTicketEntity> entities = pensionTicketRepository.findByRound(roundNumber);
            List<PensionTicketDto> tickets = new ArrayList<>();
            
            for (PensionTicketEntity entity : entities) {
                tickets.add(PensionTicketDto.from(entity));
            }

            return ResponseEntity.ok(tickets);
        } catch (Exception e) {
            log.error("❌ Error fetching tickets for round {}: {}", roundNumber, e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "오류: " + e.getMessage())
            );
        }
    }

    /**
     * 통계 조회
     * GET /api/pension/ticket/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            long totalTickets = pensionTicketRepository.count();
            Optional<PensionTicketEntity> latest = pensionTicketRepository.findLatestTicket();

            Map<String, Object> stats = new HashMap<>();
            stats.put("success", true);
            stats.put("totalTickets", totalTickets);
            stats.put("latestRound", latest.map(PensionTicketEntity::getRound).orElse(null));
            stats.put("latestTicketAt", latest.map(PensionTicketEntity::getCreatedAt).orElse(null));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("❌ Error fetching stats: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "오류: " + e.getMessage())
            );
        }
    }
}

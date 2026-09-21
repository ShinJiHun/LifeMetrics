package com.lifemetrics.backend.lotto.controller;

import com.lifemetrics.backend.lotto.dto.LottoTicketDto;
import com.lifemetrics.backend.lotto.dto.LottoTicketUploadResponse;
import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import com.lifemetrics.backend.lotto.entity.LottoTicketEntity;
import com.lifemetrics.backend.lotto.repository.LottoNumberRepository;
import com.lifemetrics.backend.lotto.repository.LottoTicketRepository;
import com.lifemetrics.backend.lotto.service.LottoTicketOcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lotto/ticket")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
public class LottoTicketController {

    private final LottoTicketRepository ticketRepo;
    private final LottoNumberRepository numberRepo;
    private final LottoTicketOcrService ocrService;

    /** 로또 용지 사진 업로드 → OCR 인식 → NAS 저장 → DB 저장. */
    @PostMapping("/upload")
    public LottoTicketUploadResponse upload(@RequestParam("file") MultipartFile file) {
        return ocrService.upload(file);
    }

    /** 로또 용지 QR코드 스캔 → 동행복권 확인 페이지 조회 → 회차/번호 인식 → DB 저장. */
    @PostMapping("/qr")
    public LottoTicketUploadResponse registerFromQr(@RequestBody LottoQrRequest req) {
        return ocrService.registerFromQr(req.qrText());
    }

    public record LottoQrRequest(String qrText) {}

    /** 내가 저장한 모든 티켓(구매 게임) 목록 - 페이지네이션 지원. 기본 20개씩. */
    @GetMapping("/list")
    public List<LottoTicketDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        List<LottoTicketEntity> tickets = ticketRepo.findAllByOrderByCreatedAtDesc(pageable);

        // 이 페이지 티켓들이 참조하는 회차를 한 번에 조회 (N+1 방지).
        List<Integer> rounds = tickets.stream()
                .map(LottoTicketEntity::getRound)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, LottoNumberEntity> winByRound = numberRepo.findAllById(rounds).stream()
                .collect(Collectors.toMap(LottoNumberEntity::getRound, w -> w));

        return tickets.stream()
                .map(ticket -> toDtoWithMatch(ticket, winByRound.get(ticket.getRound())))
                .toList();
    }

    private LottoTicketDto toDtoWithMatch(LottoTicketEntity ticket, LottoNumberEntity win) {
        if (win == null) {
            return new LottoTicketDto(ticket);
        }
        Set<Integer> winSet = Set.of(
                win.getN1(), win.getN2(), win.getN3(),
                win.getN4(), win.getN5(), win.getN6()
        );
        int[] nums = ticket.numbers();
        int match = 0;
        for (int n : nums) {
            if (winSet.contains(n)) match++;
        }
        boolean bonusMatch = false;
        if (match == 5) {
            for (int n : nums) {
                if (n == win.getBonus()) bonusMatch = true;
            }
        }
        return new LottoTicketDto(ticket, match, bonusMatch);
    }
}

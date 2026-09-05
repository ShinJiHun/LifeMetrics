package com.lifemetrics.backend.lotto.api;

import com.lifemetrics.backend.lotto.dto.LottoTicketDto;
import com.lifemetrics.backend.lotto.dto.LottoTicketUploadResponse;
import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import com.lifemetrics.backend.lotto.entity.LottoTicketEntity;
import com.lifemetrics.backend.lotto.repository.LottoNumberRepository;
import com.lifemetrics.backend.lotto.repository.LottoTicketRepository;
import com.lifemetrics.backend.lotto.service.LottoTicketOcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
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

    /** 내가 저장한 모든 티켓(구매 게임) 목록. 이미 추첨된 회차는 매칭 개수도 함께 계산해준다. */
    @GetMapping("/list")
    public List<LottoTicketDto> list() {
        List<LottoTicketEntity> tickets = ticketRepo.findAllByOrderByCreatedAtDesc();

        return tickets.stream()
                .map(this::toDtoWithMatch)
                .toList();
    }

    private LottoTicketDto toDtoWithMatch(LottoTicketEntity ticket) {
        if (ticket.getRound() == null) {
            return new LottoTicketDto(ticket);
        }
        return numberRepo.findById(ticket.getRound())
                .map(win -> {
                    Set<Integer> winSet = Set.of(
                            win.getN1(), win.getN2(), win.getN3(),
                            win.getN4(), win.getN5(), win.getN6()
                    );
                    int[] nums = ticket.numbers();
                    int match = 0;
                    for (int n : nums) {
                        if (winSet.contains(n)) match++;
                    }
                    boolean bonusMatch = match == 5;
                    if (bonusMatch) {
                        bonusMatch = false;
                        for (int n : nums) {
                            if (n == win.getBonus()) bonusMatch = true;
                        }
                    }
                    return new LottoTicketDto(ticket, match, bonusMatch);
                })
                .orElseGet(() -> new LottoTicketDto(ticket));
    }
}

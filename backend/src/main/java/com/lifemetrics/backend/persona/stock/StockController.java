package com.lifemetrics.backend.persona.stock;

import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * 증권 페르소나 — 토스증권 계좌·보유 종목·거래내역 조회.
 * 개인 금융 정보라 읽기도 관리자만 허용한다(AdminWriteFilter 는 GET 을 열어 두므로 여기서 막는다).
 */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService service;
    private final AdminWriteFilter adminWriteFilter;

    @GetMapping("/summary")
    public StockService.Summary summary(HttpServletRequest request) {
        requireAdmin(request);
        return service.summary();
    }

    @GetMapping("/transactions")
    public List<StockService.Transaction> transactions(
            HttpServletRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String symbol) {
        requireAdmin(request);
        return service.transactions(from, to, type, market, symbol);
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}

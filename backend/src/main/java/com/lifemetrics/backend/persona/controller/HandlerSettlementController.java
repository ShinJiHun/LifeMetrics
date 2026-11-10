package com.lifemetrics.backend.persona.controller;

import com.lifemetrics.backend.persona.service.HandlerSettlementParser;
import com.lifemetrics.backend.persona.service.HandlerSettlementParser.Parsed;
import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 인간 페르소나 — 핸들러(쏘카) 월별 운행료 명세서 PDF 보관 + 숫자 정리 (관리자 전용).
 * <p>
 * DB 없이 폴더 하나({@code handler.settlement.path})에 파일만 둔다.
 * 파일명 규칙: {@code YYYY-MM__원본이름.pdf}. 정산 월은 PDF 의 "귀속월" 을 우선 쓰고,
 * 못 읽으면 파일명 앞의 월을 쓴다.
 * <p>
 * 지급금액은 세전으로 보고 사업소득 원천징수 3.3%(소득세 3% + 지방소득세 0.3%)를 빼
 * 실수령 <b>추정치</b>를 함께 내려준다.
 * <p>
 * 쓰기는 AdminWriteFilter 가 막고, 읽기(목록·PDF)는 금액·개인정보가 있으므로
 * 여기서 명시적으로 관리자만 허용한다.
 */
@RestController
@RequestMapping("/api/human/handler-settlements")
@RequiredArgsConstructor
public class HandlerSettlementController {

    /** 저장된 파일명: 2026-10__원본이름.pdf */
    private static final Pattern STORED_NAME = Pattern.compile("^(\\d{4}-(0[1-9]|1[0-2]))__([^/\\\\]+)\\.pdf$");
    private static final Pattern MONTH = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private final AdminWriteFilter adminWriteFilter;
    private final HandlerSettlementParser parser;

    @Value("${handler.settlement.path:/tmp/handler-settlement/}")
    private String basePath;

    /**
     * 파일 1개 + PDF 에서 읽은 값 + 원천징수 추정.
     *
     * @param month     정산(귀속) 월. PDF 에서 읽었으면 그 값, 아니면 파일명의 월.
     * @param parsed    PDF 에서 귀속월·지급금액을 읽었는지
     * @param duplicate 같은 월에 더 최근 업로드가 있어 합계에서 빠지는 파일인지
     */
    public record SettlementFile(
            String filename, String month, String originalName, long size, Instant uploadedAt,
            boolean parsed, boolean duplicate,
            LocalDate startDate, LocalDate endDate, Integer workDays, Integer trips, String serviceType,
            LocalDate issuedDate,
            Long grossAmount, Long incomeTax, Long localTax, Long netAmount
    ) {}

    @GetMapping
    public List<SettlementFile> list(HttpServletRequest request) throws IOException {
        requireAdmin(request);
        Path dir = dir();
        if (!Files.isDirectory(dir)) return List.of();

        List<SettlementFile> all;
        try (Stream<Path> files = Files.list(dir)) {
            all = files.filter(Files::isRegularFile)
                    .map(p -> toDto(p, false))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(SettlementFile::month).reversed()
                            .thenComparing(SettlementFile::uploadedAt, Comparator.reverseOrder()))
                    .toList();
        }

        // 같은 월이 여러 개면(재다운로드 등) 가장 최근 업로드만 합계에 넣는다.
        Set<String> seen = new HashSet<>();
        List<SettlementFile> result = new ArrayList<>(all.size());
        for (SettlementFile f : all) {
            boolean dup = !seen.add(f.month());
            result.add(dup ? withDuplicate(f) : f);
        }
        return result;
    }

    /**
     * 업로드. 월은 PDF 의 귀속월을 자동으로 읽고, 못 읽을 때만 {@code month} 를 쓴다.
     * 같은 월·같은 이름이면 덮어쓴다.
     */
    @PostMapping
    public SettlementFile upload(@RequestParam(value = "month", required = false) String month,
                                 @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일입니다.");
        }
        byte[] bytes = file.getBytes();
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 파일만 올릴 수 있습니다.");
        }

        Parsed parsed = parser.parse(bytes);
        String effectiveMonth = parsed.periodMonth() != null ? parsed.periodMonth() : month;
        if (effectiveMonth == null || !MONTH.matcher(effectiveMonth).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "PDF 에서 귀속월을 읽지 못했습니다. 정산 월(YYYY-MM)을 직접 골라 주세요.");
        }

        String filename = effectiveMonth + "__" + safeBaseName(file.getOriginalFilename()) + ".pdf";
        Path dir = dir();
        Files.createDirectories(dir);
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);

        Files.write(target, bytes);
        parser.evict(target);
        return toDto(target, false);
    }

    /** 원본 PDF — 브라우저에서 바로 열리도록 inline. */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> view(HttpServletRequest request, @PathVariable String filename) {
        requireAdmin(request);
        Path path = resolveExisting(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/{filename:.+}")
    public void delete(@PathVariable String filename) throws IOException {
        Path path = resolveExisting(filename);
        Files.delete(path);
        parser.evict(path);
    }

    // ── 원천징수 (사업소득 3.3%) ─────────────────────────────

    /** 소득세 3%, 10원 미만 절사. */
    static long incomeTax(long gross) {
        return (gross * 3 / 100) / 10 * 10;
    }

    /** 지방소득세 = 소득세의 10%, 10원 미만 절사. */
    static long localTax(long incomeTax) {
        return (incomeTax / 10) / 10 * 10;
    }

    // ── helpers ─────────────────────────────

    private Path dir() {
        return Paths.get(basePath).toAbsolutePath().normalize();
    }

    private Path resolveExisting(String filename) {
        if (!STORED_NAME.matcher(filename).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path dir = dir();
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return path;
    }

    private SettlementFile toDto(Path p, boolean duplicate) {
        String name = p.getFileName().toString();
        Matcher m = STORED_NAME.matcher(name);
        if (!m.matches()) return null;
        try {
            Parsed parsed = parser.parse(p);
            String month = parsed.periodMonth() != null ? parsed.periodMonth() : m.group(1);
            Long gross = parsed.grossAmount();
            Long income = gross == null ? null : incomeTax(gross);
            Long local = income == null ? null : localTax(income);
            Long net = gross == null ? null : gross - income - local;
            return new SettlementFile(name, month, m.group(3) + ".pdf",
                    Files.size(p), Files.getLastModifiedTime(p).toInstant(),
                    parsed.ok(), duplicate,
                    parsed.startDate(), parsed.endDate(), parsed.workDays(), parsed.trips(), parsed.serviceType(),
                    parsed.issuedDate(),
                    gross, income, local, net);
        } catch (IOException e) {
            return null;
        }
    }

    private static SettlementFile withDuplicate(SettlementFile f) {
        return new SettlementFile(f.filename(), f.month(), f.originalName(), f.size(), f.uploadedAt(),
                f.parsed(), true,
                f.startDate(), f.endDate(), f.workDays(), f.trips(), f.serviceType(), f.issuedDate(),
                f.grossAmount(), f.incomeTax(), f.localTax(), f.netAmount());
    }

    /** 원본 파일명에서 확장자를 떼고 경로·특수문자를 정리한다. 한글은 유지. */
    static String safeBaseName(String original) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.toLowerCase().endsWith(".pdf")) name = name.substring(0, name.length() - 4);
        name = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").replaceAll("_{2,}", "_");
        name = name.replaceAll("^[\\s._]+|[\\s_]+$", "");
        if (name.length() > 80) name = name.substring(0, 80);
        return name.isBlank() ? "정산서" : name;
    }

    /** 화면에서 이유를 보여줄 수 있게 {message} 로 돌려준다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", msg));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}

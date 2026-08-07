package com.lifemetrics.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자 로그인 / 로그아웃 / 상태 확인.
 * <p>
 * 실제 권한 검사는 {@link AdminWriteFilter} 가 모든 쓰기 요청에 대해 수행한다.
 * 프론트는 여기 {@code /status} 로 UI 표시 여부만 판단한다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final AdminWriteFilter adminWriteFilter;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                                     HttpServletRequest request) {
        if (!adminAuthService.passwordMatches(body.get("password"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("admin", false, "error", "비밀번호가 올바르지 않습니다."));
        }

        ResponseCookie cookie = baseCookie(request, adminAuthService.issueToken())
                .maxAge(AdminAuthService.TOKEN_TTL)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("admin", true));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        ResponseCookie cookie = baseCookie(request, "").maxAge(0).build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("admin", false));
    }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request) {
        return Map.of("admin", adminWriteFilter.isAdmin(request));
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(HttpServletRequest request, String value) {
        return ResponseCookie.from(AdminWriteFilter.COOKIE_NAME, value)
                .httpOnly(true)          // JS 에서 읽을 수 없게 (XSS 로 토큰 탈취 방지)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/");
    }
}
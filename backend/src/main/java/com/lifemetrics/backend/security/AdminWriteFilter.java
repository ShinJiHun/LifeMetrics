package com.lifemetrics.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 쓰기 요청(POST/PUT/PATCH/DELETE)을 관리자에게만 허용한다.
 * <p>
 * 읽기(GET/HEAD/OPTIONS)는 누구나 가능하다. 컨트롤러 30여 개에 어노테이션을
 * 다는 대신 여기서 일괄 차단하므로, 새 쓰기 엔드포인트를 추가해도 자동으로 보호된다.
 * <p>
 * 예외적으로 열어두는 경로는 {@link #PUBLIC_WRITE_PATHS} 뿐이다. 방문자도 쓸 수
 * 있어야 하는 챗봇/예측처럼 <b>기록을 남기지 않는</b> POST 만 여기에 넣는다.
 */
@Component
@RequiredArgsConstructor
public class AdminWriteFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "lm_admin";

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * 비관리자도 호출할 수 있는 쓰기 메서드 경로.
     * <p>
     * 주의: 여기 추가하는 경로는 인증 없이 외부에 노출된다. AI 호출은 비용이
     * 발생하므로, 남용이 우려되면 레이트 제한을 별도로 걸 것.
     */
    private static final List<String> PUBLIC_WRITE_PATHS = List.of(
            "/api/admin/login",
            "/api/admin/logout",
            "/api/persona/chat",
            "/api/persona/runpod/chat",
            "/api/persona/transcribe",
            "/api/persona/runpod/chat",
            "/api/ai/chat/activity/**",
            "/api/activity/predict",
            "/api/todos/verify",
            "/api/todos/notify"
    );

    private final AdminAuthService adminAuthService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!requiresAdmin(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (isAdmin(request)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"관리자 권한이 필요합니다.\",\"code\":\"ADMIN_REQUIRED\"}");
    }

    private boolean requiresAdmin(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return false;
        if (READ_METHODS.contains(request.getMethod())) return false;
        return PUBLIC_WRITE_PATHS.stream().noneMatch(p -> pathMatcher.match(p, path));
    }

    /** 요청에 유효한 관리자 쿠키가 실려 있는지 확인한다. */
    public boolean isAdmin(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;

        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && adminAuthService.isValidToken(c.getValue())) {
                return true;
            }
        }
        return false;
    }
}
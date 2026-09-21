package com.lifemetrics.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA(React Router) 클라이언트 라우트로 직접 접속하거나 새로고침했을 때 index.html 을 내려준다.
 * (이게 없으면 /lottery/lotto/stats 같은 경로는 정적 파일이 없어서 401/404 가 된다.)
 * <p>
 * 주의: 매핑에 {@code /**} 를 쓰면 /assets/index-abc.js 같은 정적 파일까지 가로채서 index.html 을 내려주게 된다
 * (컨트롤러 매핑이 정적 리소스 핸들러보다 우선). PathPattern 의 ** 에는 정규식을 못 쓰므로
 * 경로 깊이별로 "확장자(.) 없는 세그먼트"만 매칭하는 패턴을 나열한다.
 * <p>
 * 제외: 첫 세그먼트가 api / oauth2 / login / error 인 경로, 세그먼트에 '.' 이 있는 경로(정적 파일).
 * {@link #isSpaRoute} 는 SecurityConfig 가 이 경로들을 인증 없이 열어주기 위한 판별이며 매핑과 같은 기준이어야 한다.
 */
@Controller
public class SpaForwardController {

    /** 지원하는 최대 경로 깊이. 매핑 목록과 {@link #isSpaRoute} 가 함께 따른다. */
    private static final int MAX_DEPTH = 6;

    private static final String[] NON_SPA_FIRST_SEGMENTS = {"api", "oauth2", "login", "error"};

    private static final String SEG = "[^.]+";
    private static final String FIRST = "/{s1:(?!api$|oauth2$|login$|error$)" + SEG + "}";

    @GetMapping({
            FIRST,
            FIRST + "/{s2:" + SEG + "}",
            FIRST + "/{s2:" + SEG + "}/{s3:" + SEG + "}",
            FIRST + "/{s2:" + SEG + "}/{s3:" + SEG + "}/{s4:" + SEG + "}",
            FIRST + "/{s2:" + SEG + "}/{s3:" + SEG + "}/{s4:" + SEG + "}/{s5:" + SEG + "}",
            FIRST + "/{s2:" + SEG + "}/{s3:" + SEG + "}/{s4:" + SEG + "}/{s5:" + SEG + "}/{s6:" + SEG + "}"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }

    /** GET/HEAD 이면서 {@link #forwardToIndex} 매핑이 처리하는 경로인지. */
    public static boolean isSpaRoute(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) return false;

        String path = request.getRequestURI();
        if (path == null || path.length() < 2 || path.endsWith("/")) return false;   // "/" 는 기존 permitAll 이 처리

        String[] segments = path.substring(1).split("/", -1);
        if (segments.length > MAX_DEPTH) return false;
        for (String segment : segments) {
            if (segment.isEmpty() || segment.contains(".")) return false;
        }
        for (String excluded : NON_SPA_FIRST_SEGMENTS) {
            if (segments[0].equals(excluded)) return false;
        }
        return true;
    }
}

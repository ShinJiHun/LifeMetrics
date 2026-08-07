package com.lifemetrics.backend.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 관리자(쓰기 권한) 인증.
 * <p>
 * 비밀번호는 {@code settings.password} (.env {@code SETTINGS_PASSWORD})를 그대로 쓴다.
 * 설정 모달과 같은 비밀번호이므로 새로 관리할 비밀이 늘지 않는다.
 * <p>
 * 토큰은 서버 메모리 세션이 아니라 <b>HMAC 서명 문자열</b>이다. 배포로 서버가
 * 재시작돼도 로그인이 유지된다. 서명 키는 비밀번호에서 파생하므로,
 * 비밀번호를 바꾸면 기존 토큰이 모두 무효화된다.
 * <p>
 * 형식: {@code base64url(만료epoch초) + "." + base64url(HMAC-SHA256)}
 */
@Service
public class AdminAuthService {

    /** 로그인 유지 기간. */
    public static final Duration TOKEN_TTL = Duration.ofDays(30);

    private static final String HMAC_ALG = "HmacSHA256";

    @Value("${settings.password:lifemetrics}")
    private String password;

    private byte[] signingKey;

    @PostConstruct
    void init() {
        // 서명 키를 비밀번호에서 파생한다. 별도 시크릿 환경변수가 필요 없고,
        // 비밀번호 변경이 곧 전체 로그아웃이 된다.
        signingKey = ("lm-admin-v1:" + password).getBytes(StandardCharsets.UTF_8);
    }

    /** 입력한 비밀번호가 맞는지 확인한다. 길이 노출을 피하려 상수 시간 비교를 쓴다. */
    public boolean passwordMatches(String input) {
        if (password == null || input == null) return false;
        return MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                input.getBytes(StandardCharsets.UTF_8)
        );
    }

    /** 새 관리자 토큰을 발급한다. */
    public String issueToken() {
        long expiry = Instant.now().plus(TOKEN_TTL).getEpochSecond();
        String payload = encode(String.valueOf(expiry).getBytes(StandardCharsets.UTF_8));
        return payload + "." + encode(sign(payload));
    }

    /** 토큰이 유효하고 아직 만료되지 않았는지 확인한다. */
    public boolean isValidToken(String token) {
        if (token == null || token.isBlank()) return false;

        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return false;

        String payload = token.substring(0, dot);
        byte[] presented;
        byte[] expiryBytes;
        try {
            presented = decode(token.substring(dot + 1));
            expiryBytes = decode(payload);
        } catch (IllegalArgumentException e) {
            return false;
        }

        // 서명을 먼저 검증한다. 위조된 만료시각을 신뢰하지 않기 위해서다.
        if (!MessageDigest.isEqual(sign(payload), presented)) return false;

        try {
            long expiry = Long.parseLong(new String(expiryBytes, StandardCharsets.UTF_8));
            return Instant.now().getEpochSecond() < expiry;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALG));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("관리자 토큰 서명 실패", e);
        }
    }

    private static String encode(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] decode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }
}

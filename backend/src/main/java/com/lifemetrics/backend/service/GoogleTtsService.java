package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoogleTtsService {

    @Value("${gcp.tts.base-url:https://texttospeech.googleapis.com}")
    private String baseUrl;

    @Value("${gcp.tts.language-code:ko-KR}")
    private String languageCode;

    @Value("${gcp.tts.audio-encoding:LINEAR16}")
    private String audioEncoding;

    @Value("${gcp.tts.voice.persona:ko-KR-Chirp3-HD-Kore}")
    private String voicePersona;

    @Value("${gcp.tts.voice.riding:ko-KR-Chirp3-HD-Charon}")
    private String voiceRiding;

    private static final int MAX_CHARS = 1500;

    private final ObjectMapper om = new ObjectMapper();
    private final RestTemplate rest;

    private GoogleCredentials credentials;

    public GoogleTtsService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(30_000);
        this.rest = new RestTemplate(f);
    }

    @PostConstruct
    void initCredentials() {
        try {
            this.credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        } catch (Exception e) {
            // ADC 미설정 시 null → hasApiKey() false → 호출부에서 503 처리(프론트는 조용히 스킵).
            System.out.println("⚠️ [GoogleTtsService] ADC 초기화 실패(인증 미설정): " + e.getMessage());
            this.credentials = null;
        }
    }
    public boolean hasApiKey() {
        return credentials != null;
    }
    private String resolveVoiceName(String voice) {
        if ("riding".equalsIgnoreCase(voice)) return voiceRiding;
        return voicePersona;
    }
    public byte[] synthesize(String text, String voice) {
        if (!hasApiKey() || text == null || text.isBlank()) return null;

        String voiceName = resolveVoiceName(voice);
        if (voiceName == null || voiceName.isBlank()) return null;

        String clipped = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("input", Map.of("text", clipped));
            body.put("voice", Map.of("languageCode", languageCode, "name", voiceName));
            body.put("audioConfig", Map.of("audioEncoding", audioEncoding));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken());

            HttpEntity<String> req = new HttpEntity<>(om.writeValueAsString(body), headers);
            ResponseEntity<String> res =
                    rest.postForEntity(baseUrl + "/v1/text:synthesize", req, String.class);

            // 응답은 { "audioContent": "<base64>" } 형태. 디코딩해서 바이트 반환.
            JsonNode node = om.readTree(res.getBody());
            String b64 = node.path("audioContent").asText(null);
            if (b64 == null || b64.isBlank()) return null;
            return Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            System.out.println("❌ [GoogleTtsService] TTS 에러: " + e.getMessage());
            return null;
        }
    }
    private String accessToken() throws Exception {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}

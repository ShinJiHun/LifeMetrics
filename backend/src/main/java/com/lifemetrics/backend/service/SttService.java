package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 로컬 상주 Whisper(STT) 서버({@code whisper_models/scripts/stt_server.py}) 호출 클라이언트.
 * <p>
 * 모델을 매 요청마다 로드하면 느려지므로, 별도 파이썬 프로세스로 상주시켜두고
 * 오디오 바이트를 그대로 넘겨 텍스트만 받아온다. 서버가 안 떠 있으면 null을 반환한다.
 */
@Service
public class SttService {

    @Value("${stt.base-url:http://127.0.0.1:8765}")
    private String baseUrl;

    private final ObjectMapper om = new ObjectMapper();
    private final RestTemplate rest;

    public SttService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2_000);
        f.setReadTimeout(30_000);
        this.rest = new RestTemplate(f);
    }

    /** 오디오 바이트를 로컬 Whisper 서버로 보내 인식된 텍스트를 받는다. 실패/미기동 시 null. */
    public String transcribe(byte[] audioBytes, String contentType) {
        if (audioBytes == null || audioBytes.length == 0) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    contentType != null && !contentType.isBlank() ? contentType : "audio/webm"));
            HttpEntity<byte[]> req = new HttpEntity<>(audioBytes, headers);

            ResponseEntity<String> res = rest.postForEntity(baseUrl + "/transcribe", req, String.class);
            return om.readTree(res.getBody()).path("text").asText(null);
        } catch (Exception e) {
            System.out.println("❌ [SttService] transcribe 에러: " + e.getMessage());
            return null;
        }
    }
}

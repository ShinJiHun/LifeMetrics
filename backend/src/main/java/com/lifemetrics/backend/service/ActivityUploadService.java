package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.UploadResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActivityUploadService {

    @Value("${parser.api.url}")
    private String parserApiUrl;

    private final RestTemplate restTemplate;

    public UploadResultDto.FileResult processFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    parserApiUrl + "/parse", request, Map.class);

            String status = (String) response.getBody().get("status");
            return new UploadResultDto.FileResult(filename, status, null);

        } catch (Exception e) {
            return new UploadResultDto.FileResult(filename, "fail", e.getMessage());
        }
    }
}

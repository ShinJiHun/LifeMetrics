package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.NutritionDto;
import com.lifemetrics.backend.service.GoogleFitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fitness")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true") // React(Vite) 포트 허용
public class FitnessController {

    private final GoogleFitService googleFitService;

    @GetMapping("/today-nutrition")
    public ResponseEntity<NutritionDto> getTodayNutrition(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {

        // OAuth2 인증 정보에서 Access Token 추출
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // 데이터 조회
        NutritionDto response = googleFitService.getTodayDetailedNutrition(accessToken);

        return ResponseEntity.ok(response);
    }
}

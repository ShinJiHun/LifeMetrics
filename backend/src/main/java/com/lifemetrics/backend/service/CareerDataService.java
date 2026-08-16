package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.persona.entity.UserBasicProfile;
import com.lifemetrics.backend.persona.repository.UserBasicProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * user_basic_profile.career(JSON 배열)를 읽어 페르소나 챗봇 프롬프트용 텍스트로 변환한다.
 * PDF 이력서보다 최신화가 쉬운 구조화 데이터라, 현재 재직 회사 등 최신 사실은 이 소스를 우선 신뢰한다.
 */
@Service
@RequiredArgsConstructor
public class CareerDataService {

    private final UserBasicProfileRepository userBasicProfileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isLoaded() {
        return userBasicProfileRepository.findAll().stream()
                .findFirst()
                .map(p -> p.getCareer() != null && !p.getCareer().isBlank())
                .orElse(false);
    }

    public String getText() {
        Optional<UserBasicProfile> profileOpt = userBasicProfileRepository.findAll().stream().findFirst();
        if (profileOpt.isEmpty() || profileOpt.get().getCareer() == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try {
            JsonNode companies = objectMapper.readTree(profileOpt.get().getCareer());
            for (JsonNode c : companies) {
                String name = c.path("companyName").asText("");
                boolean isCurrent = c.path("isCurrent").asBoolean(false);

                sb.append("### ").append(name);
                if (isCurrent) sb.append(" (현재 재직중)");
                sb.append("\n기간: ").append(c.path("periodLabel").asText(""))
                        .append("\n직무: ").append(c.path("role").asText("")).append("\n");

                for (JsonNode p : c.path("projects")) {
                    sb.append("- ").append(p.path("title").asText("")).append("\n");
                    for (JsonNode para : p.path("paragraphs")) {
                        sb.append("  ").append(para.asText("")).append("\n");
                    }
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            System.out.println("❌ [CareerData] career JSON 파싱 실패: " + e.getMessage());
        }
        return sb.toString();
    }
}
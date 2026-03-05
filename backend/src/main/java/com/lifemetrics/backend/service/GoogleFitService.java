package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.NutritionDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoogleFitService {

    @Value("${google.fitness.api-url}")
    private String googleApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public NutritionDto getTodayDetailedNutrition(String accessToken) {
        // 오늘 0시부터 현재까지의 타임스탬프 계산 (milliseconds)
        long startTime = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTime = System.currentTimeMillis();

        // 1. 구글 피트니스 API 요청 바디 구성
        Map<String, Object> aggregateBy = new HashMap<>();
        aggregateBy.put("dataTypeName", "com.google.nutrition");

        Map<String, Object> bucketByTime = new HashMap<>();
        bucketByTime.put("durationMillis", 86400000); // 24시간 단위

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("aggregateBy", List.of(aggregateBy));
        requestBody.put("bucketByTime", bucketByTime);
        requestBody.put("startTimeMillis", startTime);
        requestBody.put("endTimeMillis", endTime);

        // 2. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        NutritionDto nutrition = NutritionDto.builder().status("no_data").build();

        try {
            // 3. API 호출
            ResponseEntity<Map> response = restTemplate.postForEntity(googleApiUrl, entity, Map.class);
            Map<String, Object> body = response.getBody();

            System.out.println("구글 API 응답 원본: " + body.toString());

            if (body != null && body.containsKey("bucket")) {
                List<Map<String, Object>> buckets = (List<Map<String, Object>>) body.get("bucket");

                for (Map<String, Object> bucket : buckets) {
                    List<Map<String, Object>> datasets = (List<Map<String, Object>>) bucket.get("dataset");

                    for (Map<String, Object> dataset : datasets) {
                        List<Map<String, Object>> points = (List<Map<String, Object>>) dataset.get("point");

                        if (!points.isEmpty()) {
                            nutrition.setStatus("success");
                        }

                        for (Map<String, Object> point : points) {
                            List<Map<String, Object>> values = (List<Map<String, Object>>) point.get("value");
                            for (Map<String, Object> value : values) {
                                if (value.containsKey("mapVal")) {
                                    List<Map<String, Object>> mapEntries = (List<Map<String, Object>>) value.get("mapVal");
                                    for (Map<String, Object> entry : mapEntries) {
                                        String key = (String) entry.get("key");

                                        // [수정 포인트] 구글 응답 원본의 value가 {fpVal=939} 형태이므로 한 번 더 꺼내야 함
                                        Object innerValueObj = entry.get("value");
                                        if (innerValueObj instanceof Map) {
                                            Map<String, Object> innerMap = (Map<String, Object>) innerValueObj;
                                            double val = 0.0;
                                            if (innerMap.containsKey("fpVal")) {
                                                val = Double.parseDouble(innerMap.get("fpVal").toString());
                                            }

                                            switch (key) {
                                                case "calories": nutrition.setCalories(nutrition.getCalories() + val); break;
                                                case "protein": nutrition.setProtein(nutrition.getProtein() + val); break;
                                                case "fat.total": nutrition.setFat(nutrition.getFat() + val); break;
                                                case "carbs.total": nutrition.setCarbs(nutrition.getCarbs() + val); break;
                                                case "sugar": nutrition.setSugar(nutrition.getSugar() + val); break;
                                                case "dietary_fiber": nutrition.setFiber(nutrition.getFiber() + val); break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Google Fit API 호출 에러: " + e.getMessage());
            nutrition.setStatus("error");
        }

        return nutrition;
    }

    // JSON 숫자를 안전하게 double로 변환하는 헬퍼 메소드
    private double convertToDouble(Object obj) {
        if (obj instanceof Integer) return ((Integer) obj).doubleValue();
        if (obj instanceof Double) return (Double) obj;
        if (obj instanceof Float) return ((Float) obj).doubleValue();
        if (obj instanceof Long) return ((Long) obj).doubleValue();
        return 0.0;
    }
}

package com.lifemetrics.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.entity.Bike;
import com.lifemetrics.backend.repository.BikeRepository;
import com.lifemetrics.backend.service.BikeSpecExtractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bikes")
@RequiredArgsConstructor
public class BikeController {

    private final BikeSpecExtractService extractService;
    private final BikeRepository bikeRepository;

    @GetMapping
    public List<Bike> list() {
        return bikeRepository.findByIsRetiredFalseOrderByIsDefaultDescIdDesc();
    }

    // 모델명 검색 → 사양 prefill JSON (저장 안 함)
    @PostMapping("/search-spec")
    public ResponseEntity<JsonNode> searchSpec(@RequestBody Map<String, String> req) {
        return ResponseEntity.ok(extractService.searchSpec(req.get("model")));
    }

    // 스펙 이미지 업로드 → 사양 prefill JSON (저장 안 함)
    @PostMapping(value = "/extract-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JsonNode> extractImage(@RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(extractService.extractFromImage(image));
    }

    // 폼에서 검수/수정한 최종 자전거 저장
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Bike> save(@RequestBody Bike bike) {
        if (bike.getName() == null || bike.getName().isBlank())
            bike.setName(bike.getModel() != null ? bike.getModel() : bike.getBrand());
        return ResponseEntity.ok(bikeRepository.save(bike));
    }
}
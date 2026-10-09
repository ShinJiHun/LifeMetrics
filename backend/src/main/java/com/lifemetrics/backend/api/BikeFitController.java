package com.lifemetrics.backend.api;

import com.lifemetrics.backend.entity.BikeFit;
import com.lifemetrics.backend.repository.BikeFitRepository;
import com.lifemetrics.backend.repository.BikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 자전거 피팅 기록. 쓰기(POST/PUT)는 AdminWriteFilter 가 관리자만 허용한다.
 */
@RestController
@RequiredArgsConstructor
public class BikeFitController {

    private final BikeFitRepository fitRepository;
    private final BikeRepository bikeRepository;

    /** 최신 피팅부터 */
    @GetMapping("/api/bikes/{bikeId}/fits")
    public List<BikeFit> list(@PathVariable Long bikeId) {
        return fitRepository.findByBikeIdOrderByFitDateDescIdDesc(bikeId);
    }

    @PostMapping("/api/bikes/{bikeId}/fits")
    public ResponseEntity<BikeFit> create(@PathVariable Long bikeId, @RequestBody BikeFit request) {
        if (!bikeRepository.existsById(bikeId)) return ResponseEntity.notFound().build();
        if (request.getFitDate() == null) return ResponseEntity.badRequest().build();
        request.setId(null);
        request.setBikeId(bikeId);
        return ResponseEntity.ok(fitRepository.save(request));
    }

    @PutMapping("/api/bike-fits/{id}")
    public ResponseEntity<BikeFit> update(@PathVariable Long id, @RequestBody BikeFit request) {
        return fitRepository.findById(id)
                .map(existing -> {
                    if (request.getFitDate() == null) return ResponseEntity.badRequest().<BikeFit>build();
                    request.setId(existing.getId());
                    request.setBikeId(existing.getBikeId());
                    return ResponseEntity.ok(fitRepository.save(request));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.HumanModelResponse;
import com.lifemetrics.backend.service.HumanModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/human-model")
@RequiredArgsConstructor
public class HumanModelController {

    private final HumanModelService humanModelService;

    @GetMapping
    public HumanModelResponse getHumanModel(
            @RequestParam(defaultValue = "1") Long userId
    ) {
        return humanModelService.getHumanModel(userId);
    }
}

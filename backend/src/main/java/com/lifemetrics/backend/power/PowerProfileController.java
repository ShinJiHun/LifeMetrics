package com.lifemetrics.backend.power;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/power")
@RequiredArgsConstructor
public class PowerProfileController {

    private final PowerProfileService service;

    @GetMapping("/profile")
    public PowerProfileService.Profile profile(@RequestParam(defaultValue = "1") long userId) {
        return service.profile(userId);
    }
}

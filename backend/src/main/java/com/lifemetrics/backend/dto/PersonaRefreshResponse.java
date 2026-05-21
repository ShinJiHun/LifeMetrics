package com.lifemetrics.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonaRefreshResponse {
    private int crawledPosts;
    private boolean profileGenerated;
    private String message;
}
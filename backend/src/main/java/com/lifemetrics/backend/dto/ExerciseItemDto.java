package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExerciseItemDto {
    private Long id;
    private Long categoryId;
    private String nameKo;
    private String nameEn;
    private String description;
    private String equipmentType;
    private String gifUrl;       // /api/exercise/gif/{id} 형태
    private String youtubeUrl;
    private String mediaUrl;
}

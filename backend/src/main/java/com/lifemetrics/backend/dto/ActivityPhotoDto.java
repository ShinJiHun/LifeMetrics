package com.lifemetrics.backend.dto;

import java.time.LocalDateTime;

/** 활동 상세 지도·사진 목록용. url 은 /api/activity/photos/{id}/file?size=thumb|web|original */
public record ActivityPhotoDto(
        Long id,
        Integer sortOrder,
        String thumbUrl,
        String webUrl,
        String originalUrl,
        LocalDateTime takenAt,
        Double lat,
        Double lon,
        Double distanceKm,
        String locationSource,
        String caption,
        Integer width,
        Integer height,
        Integer blogPostNo,
        String blogPostTitle,
        String blogPostUrl
) {
}

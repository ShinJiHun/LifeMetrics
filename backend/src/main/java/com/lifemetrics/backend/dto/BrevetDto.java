package com.lifemetrics.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BrevetDto {
    private String date;       // "3월07일"
    private String region;     // "서울/경기"
    private String name;       // "200 (서)"
    private String distance;   // "200", "300", ...
    private String link;       // "/jsp/info_BV/info-s200w" (없으면 null)
    private boolean hasLink;   // 링크 있으면 신청 가능
}
